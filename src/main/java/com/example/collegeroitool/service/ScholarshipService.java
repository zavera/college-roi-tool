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
     * Searches external scholarship databases based on student demographics + extracted doc KV.
     * Returns a JSON string of scholarship objects.
     */
    public String searchExternal(Long studentId, Map<String, Object> demographics, String comments) throws Exception {
        String kvSummary = buildKvSummary(studentId);
        List<String> queries = buildExternalQueries(demographics);
        String searchResults = runSearches(queries, EXCLUDED_GENERAL);
        String context = buildContext(demographics, kvSummary, comments);
        return groqService.getScholarshipRecommendations(context, searchResults, false);
    }

    /**
     * Searches school-specific scholarship pages for each target school.
     * Returns a JSON string of scholarship objects.
     */
    public String searchSchoolSpecific(Long studentId, Map<String, Object> demographics,
                                        String comments, List<String> targetSchools) throws Exception {
        String kvSummary = buildKvSummary(studentId);
        List<String> queries = buildSchoolQueries(demographics, targetSchools);
        String searchResults = runSearches(queries, EXCLUDED_GENERAL);
        String context = buildContext(demographics, kvSummary, comments);
        return groqService.getScholarshipRecommendations(context, searchResults, true);
    }

    /**
     * Generates an ordered action timeline for the selected scholarships.
     */
    public String generateTimeline(String selectedScholarshipsJson, String studentName) {
        return groqService.getScholarshipTimeline(selectedScholarshipsJson, studentName);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private String buildKvSummary(Long studentId) {
        if (studentId == null) return "";
        try {
            Map<String, String> kv = studentDocumentService.getActiveKvPayload(studentId);
            if (kv.isEmpty()) return "";
            // Keep only financial fields relevant to scholarships (no PII)
            Map<String, String> filtered = kv.entrySet().stream()
                .filter(e -> {
                    String k = e.getKey().toLowerCase();
                    return k.contains("gpa") || k.contains("income") || k.contains("agi")
                        || k.contains("wage") || k.contains("grant") || k.contains("aid")
                        || k.contains("major") || k.contains("degree") || k.contains("school")
                        || k.contains("gpa") || k.contains("credit");
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return filtered.isEmpty() ? "" : objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> buildExternalQueries(Map<String, Object> demographics) {
        List<String> queries = new ArrayList<>();
        String year = String.valueOf(java.time.Year.now().getValue());
        String major = str(demographics, "major");
        String state = str(demographics, "state");
        String ethnicity = str(demographics, "ethnicity");
        boolean firstGen = bool(demographics, "firstGen");
        boolean needBased = "High".equalsIgnoreCase(str(demographics, "financialNeed"));

        if (!major.isEmpty()) queries.add("scholarships for " + major + " students " + year);
        if (!state.isEmpty()) queries.add(state + " scholarships undergraduate students " + year);
        if (!ethnicity.isEmpty()) queries.add(ethnicity + " student scholarships " + year);
        if (firstGen) queries.add("first generation college student scholarships " + year);
        if (needBased) queries.add("need-based scholarships undergraduate " + year);
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

    private String buildContext(Map<String, Object> demographics, String kvSummary, String comments) {
        StringBuilder sb = new StringBuilder();
        sb.append("GPA: ").append(str(demographics, "gpa")).append("\n");
        sb.append("Major/Field: ").append(str(demographics, "major")).append("\n");
        sb.append("State: ").append(str(demographics, "state")).append("\n");
        sb.append("Citizenship: ").append(str(demographics, "citizenship")).append("\n");
        String eth = str(demographics, "ethnicity");
        if (!eth.isEmpty()) sb.append("Ethnicity: ").append(eth).append("\n");
        sb.append("First-generation: ").append(bool(demographics, "firstGen") ? "Yes" : "No").append("\n");
        sb.append("Financial need level: ").append(str(demographics, "financialNeed")).append("\n");
        String extra = str(demographics, "extracurriculars");
        if (!extra.isEmpty()) sb.append("Extracurriculars/Achievements: ").append(extra).append("\n");
        if (!kvSummary.isEmpty()) sb.append("Financial context from documents: ").append(kvSummary).append("\n");
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
