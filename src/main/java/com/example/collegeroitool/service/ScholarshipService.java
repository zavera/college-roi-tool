package com.example.collegeroitool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScholarshipService {

    private static final List<String> SCHOLARSHIP_ALLOWED_DOMAINS = List.of(
        "fastweb.com", "scholarships.com", "scholarships360.org", "bold.org",
        "niche.com", "cappex.com", "goingmerry.com", "collegescholarships.org",
        "studentaid.gov", "unigo.com", "collegexpress.com", "petersons.com",
        "collegeboard.org", "salliemae.com"
    );

    // Domains Tavily should NOT search for general scholarships
    private static final List<String> EXCLUDED_GENERAL = List.of(
        "reddit.com", "quora.com", "yahoo.com", "pinterest.com"
    );

    private final TavilySearchClient tavilySearchClient;
    private final GroqService groqService;
    private final StudentDocumentService studentDocumentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScholarshipService(TavilySearchClient tavilySearchClient,
                               GroqService groqService,
                               StudentDocumentService studentDocumentService) {
        this.tavilySearchClient = tavilySearchClient;
        this.groqService = groqService;
        this.studentDocumentService = studentDocumentService;
    }

    /**
     * Unified search: combines external national/state + school-specific queries into one
     * Groq call, returning a merged de-duplicated list ordered by match quality.
     */
    public String search(Long studentId, Map<String, Object> demographics,
                         String comments, List<String> targetSchools) throws Exception {
        String kvSummary = buildKvSummary(studentId);
        List<String> queries = new ArrayList<>(buildExternalQueries(demographics));
        if (targetSchools != null && !targetSchools.isEmpty()) {
            queries.addAll(buildSchoolQueries(demographics, targetSchools));
        }
        String searchResults = runSearches(queries, EXCLUDED_GENERAL);
        String context = buildContext(demographics, kvSummary, comments, targetSchools);
        return groqService.getScholarshipRecommendations(context, searchResults);
    }

    /** @deprecated Use {@link #search} */
    public String searchExternal(Long studentId, Map<String, Object> demographics, String comments) throws Exception {
        return search(studentId, demographics, comments, List.of());
    }

    /** @deprecated Use {@link #search} */
    public String searchSchoolSpecific(Long studentId, Map<String, Object> demographics,
                                        String comments, List<String> targetSchools) throws Exception {
        return search(studentId, demographics, comments, targetSchools);
    }

    /**
     * Generates an ordered action timeline for the selected scholarships.
     */
    public String generateTimeline(String selectedScholarshipsJson, String studentName) {
        return groqService.getScholarshipTimeline(selectedScholarshipsJson, studentName);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    // FERPA: fields that are NEVER sent to any third-party service regardless of key name
    private static final List<String> PII_KEY_FRAGMENTS = List.of(
        "name", "ssn", "social", "dob", "birth", "address", "street", "zip", "phone",
        "email", "ein", "tin", "passport", "license", "account", "routing", "signature"
    );

    // FERPA: only these financial/academic field types may be sent to Groq for scholarship matching
    private static final List<String> ALLOWED_KEY_FRAGMENTS = List.of(
        "gpa", "income", "agi", "wage", "grant", "aid", "major", "degree", "school", "credit",
        "tuition", "enrollment", "field", "program", "efc", "sai", "tax"
    );

    /**
     * Builds a sanitized summary of extracted document KV for use in Groq prompts.
     * FERPA compliance: whitelists only financial/academic field types, then explicitly
     * strips any key that matches a known PII pattern — belt-and-suspenders approach.
     * This summary is sent only to Groq (an approved data processor), never to Tavily.
     */
    private String buildKvSummary(Long studentId) {
        if (studentId == null) return "";
        try {
            Map<String, String> kv = studentDocumentService.getActiveKvPayload(studentId);
            if (kv.isEmpty()) return "";
            Map<String, String> filtered = kv.entrySet().stream()
                .filter(e -> {
                    String k = e.getKey().toLowerCase();
                    // Step 1 — explicit PII exclusion (must pass)
                    boolean hasPii = PII_KEY_FRAGMENTS.stream().anyMatch(k::contains);
                    if (hasPii) return false;
                    // Step 2 — whitelist: only retain known financial/academic fields
                    return ALLOWED_KEY_FRAGMENTS.stream().anyMatch(k::contains);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return filtered.isEmpty() ? "" : objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * FERPA: Tavily search queries contain ONLY demographic/academic attributes (major, state,
     * ethnicity, firstGen). Student name, DOB, SSN, and all PII are never included in any
     * query string sent to Tavily.
     */
    private List<String> buildExternalQueries(Map<String, Object> demographics) {
        List<String> queries = new ArrayList<>();
        String year = String.valueOf(java.time.Year.now().getValue());
        String major = str(demographics, "major");
        String state = str(demographics, "state");
        String ethnicity = str(demographics, "ethnicity");
        boolean firstGen = bool(demographics, "firstGen");

        if (!major.isEmpty()) queries.add("scholarships for " + major + " students " + year);
        if (!state.isEmpty()) queries.add(state + " scholarships undergraduate students " + year);
        if (!ethnicity.isEmpty()) queries.add(ethnicity + " student scholarships " + year);
        if (firstGen) queries.add("first generation college student scholarships " + year);
        queries.add("national merit undergraduate scholarships " + year);
        return queries;
    }

    private List<String> buildSchoolQueries(Map<String, Object> demographics, List<String> schools) {
        List<String> queries = new ArrayList<>();
        String major = str(demographics, "major");
        for (String school : schools) {
            String s = school.trim();
            if (s.isEmpty()) continue;
            queries.add(s + " scholarships " + major + " students site:" + schoolDomain(s));
            queries.add(s + " financial aid merit scholarships undergraduate");
        }
        if (queries.isEmpty()) queries.add("university merit scholarships undergraduate " + major);
        return queries;
    }

    private String runSearches(List<String> queries, List<String> excluded) {
        List<Map<String, Object>> all = new ArrayList<>();
        int perQuery = Math.max(2, 8 / Math.max(1, queries.size()));
        for (String q : queries.subList(0, Math.min(queries.size(), 5))) {
            try {
                all.addAll(tavilySearchClient.search(q, perQuery, excluded));
            } catch (Exception ignored) {}
        }
        // Deduplicate by URL
        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> r : all) {
            String url = String.valueOf(r.getOrDefault("url", ""));
            deduped.putIfAbsent(url, r);
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(deduped.values()).subList(
                0, Math.min(deduped.size(), 12)));
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * FERPA: this context is sent to Groq (approved processor). It contains only
     * demographic/academic attributes and sanitized financial aggregates from buildKvSummary().
     * Student name, DOB, SSN, and all direct identifiers are intentionally excluded.
     */
    private String buildContext(Map<String, Object> demographics, String kvSummary,
                                 String comments, List<String> targetSchools) {
        StringBuilder sb = new StringBuilder();
        String gpa = str(demographics, "gpa");
        if (!gpa.isEmpty()) sb.append("GPA: ").append(gpa).append("\n");
        String major = str(demographics, "major");
        if (!major.isEmpty()) sb.append("Major/Field: ").append(major).append("\n");
        String state = str(demographics, "state");
        if (!state.isEmpty()) sb.append("State of residence: ").append(state).append("\n");
        String citizenship = str(demographics, "citizenship");
        if (!citizenship.isEmpty()) sb.append("Citizenship: ").append(citizenship).append("\n");
        String eth = str(demographics, "ethnicity");
        if (!eth.isEmpty()) sb.append("Ethnicity: ").append(eth).append("\n");
        sb.append("First-generation student: ").append(bool(demographics, "firstGen") ? "Yes" : "No").append("\n");
        String extra = str(demographics, "extracurriculars");
        if (!extra.isEmpty()) sb.append("Extracurriculars/Achievements: ").append(extra).append("\n");
        if (targetSchools != null && !targetSchools.isEmpty())
            sb.append("Target schools: ").append(String.join(", ", targetSchools)).append("\n");
        if (!kvSummary.isEmpty()) sb.append("Financial context from documents (anonymized): ").append(kvSummary).append("\n");
        if (comments != null && !comments.isBlank()) sb.append("Additional notes: ").append(comments).append("\n");
        return sb.toString();
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    private String schoolDomain(String schoolName) {
        return schoolName.toLowerCase()
            .replaceAll("\\b(university|college|institute|of|the|and|&)\\b", "")
            .replaceAll("[^a-z0-9]", "").trim() + ".edu";
    }
}
