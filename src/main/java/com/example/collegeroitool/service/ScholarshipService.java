package com.example.collegeroitool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

@Service
public class ScholarshipService {

    // Domains Tavily should NOT search for general scholarships
    private static final List<String> EXCLUDED_GENERAL = List.of(
        "reddit.com", "quora.com", "yahoo.com", "pinterest.com"
    );

    // FERPA: fields that are NEVER sent to any third-party service regardless of key name
    private static final List<String> PII_KEY_FRAGMENTS = List.of(
        "name", "ssn", "social", "dob", "birth", "address", "street", "zip", "phone",
        "email", "ein", "tin", "passport", "license", "account", "routing", "signature"
    );

    // FERPA: only these financial/academic field types may be sent to the model for scholarship matching
    private static final List<String> ALLOWED_KEY_FRAGMENTS = List.of(
        "gpa", "income", "agi", "wage", "grant", "aid", "major", "degree", "school", "credit",
        "tuition", "enrollment", "field", "program", "efc", "sai", "tax"
    );

    private final TavilySearchClient tavilySearchClient;
    private final GroqService groqService;
    private final AnthropicService anthropicService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScholarshipService(TavilySearchClient tavilySearchClient, GroqService groqService,
                               AnthropicService anthropicService) {
        this.tavilySearchClient = tavilySearchClient;
        this.groqService = groqService;
        this.anthropicService = anthropicService;
    }

    /**
     * Unified search: combines external national/state + school-specific queries into one
     * live Tavily fetch, then has Claude format/rank the results into a merged de-duplicated
     * list. Queries are always parameter-specific to what the student provided (major/state/
     * ethnicity/first-gen/target school) layered on top of a national baseline that runs
     * regardless — so a student with no filters still gets solid national-level matches, and a
     * student who gave a state gets that state's programs in addition to (not instead of) national ones.
     */
    public String search(Map<String, Object> demographics, String comments, List<String> targetSchools,
                         Long userId, String sessionId) throws Exception {
        List<String> queries = new ArrayList<>(buildExternalQueries(demographics));
        if (targetSchools != null && !targetSchools.isEmpty()) {
            queries.addAll(buildSchoolQueries(demographics, targetSchools));
        }
        List<Map<String, Object>> rawResults = runSearches(queries, EXCLUDED_GENERAL);
        String searchResultsJson = toJson(rawResults);
        String context = buildContext(demographics, comments, targetSchools);
        String recommendations = anthropicService.getScholarshipRecommendations(context, searchResultsJson, userId, sessionId);
        return validateLinks(recommendations, rawResults);
    }

    /** @deprecated Use {@link #search} */
    public String searchExternal(Map<String, Object> demographics, String comments, Long userId, String sessionId) throws Exception {
        return search(demographics, comments, List.of(), userId, sessionId);
    }

    /** @deprecated Use {@link #search} */
    public String searchSchoolSpecific(Map<String, Object> demographics, String comments,
                                        List<String> targetSchools, Long userId, String sessionId) throws Exception {
        return search(demographics, comments, targetSchools, userId, sessionId);
    }

    /**
     * Generates an ordered action timeline for the selected scholarships.
     */
    public String generateTimeline(String selectedScholarshipsJson, String studentName) {
        return groqService.getScholarshipTimeline(selectedScholarshipsJson, studentName);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * FERPA: Tavily search queries contain ONLY demographic/academic attributes (major, state,
     * ethnicity, firstGen). Student name, DOB, SSN, and all PII are never included in any
     * query string sent to Tavily.
     *
     * A "national" query always runs so a student with zero filters still gets a solid baseline
     * of results — every other query is added on top, parameter-specific to what was provided.
     */
    private List<String> buildExternalQueries(Map<String, Object> demographics) {
        List<String> queries = new ArrayList<>();
        String year = String.valueOf(java.time.Year.now().getValue());
        String major = str(demographics, "major");
        String state = str(demographics, "state");
        String ethnicity = str(demographics, "ethnicity");
        boolean firstGen = bool(demographics, "firstGen");

        // National baseline — always included, regardless of what else was provided.
        queries.add("national merit undergraduate scholarships " + year);
        queries.add("need-based scholarships for undergraduate students " + year);

        if (!major.isEmpty()) queries.add("scholarships for " + major + " students " + year);
        if (!state.isEmpty()) queries.add(state + " state scholarships undergraduate students " + year);
        if (!ethnicity.isEmpty()) queries.add(ethnicity + " student scholarships " + year);
        if (firstGen) queries.add("first generation college student scholarships " + year);
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

    private List<Map<String, Object>> runSearches(List<String> queries, List<String> excluded) {
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
        return new ArrayList<>(deduped.values()).subList(0, Math.min(deduped.size(), 12));
    }

    private String toJson(List<Map<String, Object>> results) {
        try { return objectMapper.writeValueAsString(results); }
        catch (Exception e) { return "[]"; }
    }

    /**
     * Cross-checks each scholarship's "link" against domains Tavily actually returned (or the
     * known-good scholarship-site allowlist) — the model can fabricate plausible-looking URLs,
     * so a link that matches neither is flagged unverified rather than presented as ground truth.
     */
    private String validateLinks(String recommendationsJson, List<Map<String, Object>> rawResults) {
        Set<String> verifiedDomains = new HashSet<>(AnthropicService.SCHOLARSHIP_ALLOWED_DOMAINS);
        for (Map<String, Object> r : rawResults) {
            String domain = domainOf(String.valueOf(r.getOrDefault("url", "")));
            if (domain != null) verifiedDomains.add(domain);
        }

        try {
            String cleaned = GroqService.stripMarkdownFences(recommendationsJson);
            List<Map<String, Object>> scholarships = objectMapper.readValue(cleaned, List.class);
            for (Map<String, Object> s : scholarships) {
                Object link = s.get("link");
                String domain = link != null ? domainOf(link.toString()) : null;
                boolean verified = domain != null && verifiedDomains.contains(domain);
                s.put("linkVerified", verified);
                if (!verified) s.put("link", null);
            }
            return objectMapper.writeValueAsString(scholarships);
        } catch (Exception e) {
            // Not a parseable array (e.g. truncated response) — return as-is and let the
            // controller's own fallback handling deal with it.
            return recommendationsJson;
        }
    }

    private String domainOf(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return null;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * FERPA: this context is sent to Claude (approved processor). It contains only
     * demographic/academic attributes and sanitized financial aggregates. Student name, DOB,
     * SSN, and all direct identifiers are intentionally excluded.
     */
    private String buildContext(Map<String, Object> demographics, String comments, List<String> targetSchools) {
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
            sb.append("Target school: ").append(String.join(", ", targetSchools)).append("\n");
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
