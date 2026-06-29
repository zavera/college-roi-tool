package com.example.collegeroitool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScholarshipService {

    // Official sources only — government, accredited scholarship platforms, .edu
    private static final List<String> OFFICIAL_SCHOLARSHIP_DOMAINS = List.of(
        "studentaid.gov", "collegeboard.org", "scholarships.com", "scholarships360.org",
        "fastweb.com", "goingmerry.com", "bold.org", "cappex.com",
        "hsf.net", "thegatesscholarship.org", "jkcf.org", "questbridge.org",
        "elks.org", "dellscholars.org", "afcea.org", "smartscholarship.org"
    );

    private static final List<String> OFFICIAL_STATE_TLD = List.of(".gov", ".edu");

    private static final int CONTENT_MAX_LEN = 1800; // chars per result injected into prompt

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
        String state  = str(demographics, "state");
        String major  = str(demographics, "major");

        // Build queries for each category — official sources first
        List<SearchQuery> queries = new ArrayList<>();

        // State-specific: target the state's official higher-ed / grant sites
        if (!state.isEmpty()) {
            queries.add(new SearchQuery(
                state + " state grants scholarships undergraduate students site:.gov OR site:.edu",
                buildStateDomains(state), 3));
            queries.add(new SearchQuery(
                state + " higher education commission scholarships financial aid programs",
                OFFICIAL_SCHOLARSHIP_DOMAINS, 2));
        }

        // School-specific: target each school's .edu financial aid pages
        if (targetSchools != null) {
            for (String school : targetSchools) {
                String domain = schoolDomain(school);
                queries.add(new SearchQuery(
                    school + " scholarships merit financial aid " + major,
                    List.of(domain), 3));
            }
        }

        // National/major/demographic official sources
        if (!major.isEmpty())
            queries.add(new SearchQuery("scholarships " + major + " undergraduate " + java.time.Year.now().getValue(),
                OFFICIAL_SCHOLARSHIP_DOMAINS, 3));
        queries.add(new SearchQuery("national scholarships undergraduate financial aid " + java.time.Year.now().getValue(),
            OFFICIAL_SCHOLARSHIP_DOMAINS, 3));
        if (bool(demographics, "firstGen"))
            queries.add(new SearchQuery("first generation college student scholarships grants",
                OFFICIAL_SCHOLARSHIP_DOMAINS, 2));
        String eth = str(demographics, "ethnicity");
        if (!eth.isEmpty())
            queries.add(new SearchQuery(eth + " student scholarships grants",
                OFFICIAL_SCHOLARSHIP_DOMAINS, 2));

        String liveContent = runOfficialSearches(queries);
        String context = buildContext(demographics, kvSummary, comments, targetSchools);
        return groqService.getScholarshipRecommendations(context, liveContent);
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
    private record SearchQuery(String query, List<String> domains, int maxResults) {}

    private List<String> buildStateDomains(String state) {
        // Include known state higher-ed domain patterns + official scholarship platforms
        String slug = state.toLowerCase().replaceAll("[^a-z]", "");
        List<String> domains = new ArrayList<>(OFFICIAL_SCHOLARSHIP_DOMAINS);
        // Add likely state higher-ed commission domains
        domains.add(slug + "hed.gov");
        domains.add(slug + "highered.gov");
        domains.add(slug + ".gov");
        return domains;
    }

    /**
     * Runs each query against official sources using advanced depth + raw content.
     * Returns a formatted string of live source content for prompt injection.
     */
    private String runOfficialSearches(List<SearchQuery> queries) {
        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (SearchQuery sq : queries.subList(0, Math.min(queries.size(), 6))) {
            try {
                List<Map<String, Object>> results = tavilySearchClient.searchOfficialSources(
                    sq.query(), sq.maxResults(), sq.domains(), CONTENT_MAX_LEN);
                for (Map<String, Object> r : results) {
                    String url = String.valueOf(r.getOrDefault("url", ""));
                    deduped.putIfAbsent(url, r);
                }
            } catch (Exception ignored) {}
        }
        if (deduped.isEmpty()) return "(no live results retrieved)";

        // Format as readable text blocks for prompt injection (not just JSON snippets)
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Map<String, Object> r : deduped.values()) {
            sb.append("--- SOURCE ").append(i++).append(": ").append(r.get("title")).append(" ---\n");
            sb.append("URL: ").append(r.get("url")).append("\n");
            sb.append(r.get("content")).append("\n\n");
        }
        return sb.toString();
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
