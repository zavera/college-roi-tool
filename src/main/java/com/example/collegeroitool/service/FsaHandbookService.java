package com.example.collegeroitool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches live FSA Handbook content from fsapartners.ed.gov via Tavily,
 * selecting the correct award-year edition based on the student's planning year
 * or the current FAFSA cycle.
 *
 * Award-year logic:
 *   - If planningYear is known and in the future, use the handbook for that award year.
 *   - Otherwise, derive from today's date: FAFSA for AY N-(N+1) opens October 1 of year N-1.
 *     After October 1, the "current" cycle points to the next academic year.
 */
@Service
public class FsaHandbookService {

    private static final Logger log = LoggerFactory.getLogger(FsaHandbookService.class);
    private static final List<String> FSA_DOMAINS = List.of("fsapartners.ed.gov");
    private static final int CONTENT_MAX_LEN = 1200;
    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("\\b(20[12]\\d)\\b");

    private final TavilySearchClient tavilyClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FsaHandbookService(TavilySearchClient tavilyClient) {
        this.tavilyClient = tavilyClient;
    }

    /**
     * The FAFSA award year for a given college start (fall enrollment) year.
     * Fall 2027 → "2027-2028"
     */
    public String awardYearForStartYear(int startYear) {
        return startYear + "-" + (startYear + 1);
    }

    /**
     * The tax year whose data the FAFSA will use — always 2 years before the fall start year.
     * Fall 2027 → 2025 tax data.
     */
    public int expectedTaxYear(int startYear) {
        return startYear - 2;
    }

    /**
     * Scans all KV pairs in the extracted JSON for a recognisable tax year.
     * Looks at keys containing "year", "period", "tax" and their values for a 4-digit year >= 2018.
     * Returns null if no year is found.
     */
    public Integer detectTaxYearFromKv(String kvJson) {
        if (kvJson == null || kvJson.isBlank() || kvJson.equals("{}")) return null;
        try {
            JsonNode root = objectMapper.readTree(kvJson);
            return scanNode(root);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer scanNode(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey().toLowerCase();
                JsonNode val = entry.getValue();
                // prioritise keys that are explicitly about tax year
                if (key.contains("tax") && key.contains("year") || key.equals("taxyear") || key.equals("tax year")) {
                    Integer y = extractYear(val.asText());
                    if (y != null) return y;
                }
                // fallback: any key with "year" or "period"
                if (key.contains("year") || key.contains("period")) {
                    Integer y = extractYear(val.asText());
                    if (y != null) return y;
                }
                // recurse into nested objects
                if (val.isObject() || val.isArray()) {
                    Integer y = scanNode(val);
                    if (y != null) return y;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Integer y = scanNode(child);
                if (y != null) return y;
            }
        }
        return null;
    }

    private Integer extractYear(String text) {
        if (text == null) return null;
        Matcher m = FOUR_DIGIT_YEAR.matcher(text);
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y >= 2018 && y <= LocalDate.now().getYear()) return y;
        }
        return null;
    }

    /** Returns the award year string (e.g. "2026-2027") for a given planning year or current cycle. */
    public String resolveAwardYear(Integer planningYear) {
        LocalDate today = LocalDate.now();
        int startYear;
        if (planningYear != null && planningYear >= today.getYear()) {
            // planningYear is the fall semester year, so handbook is planningYear-1 to planningYear
            // but the FAFSA award year aligns: AY 2026-2027 covers fall 2026 enrollment
            startYear = planningYear - 1;
            // Ensure we're not pointing to a year before the current FAFSA cycle
            int minStart = today.getMonthValue() >= 10 ? today.getYear() + 1 : today.getYear();
            if (startYear < minStart - 1) startYear = minStart - 1;
        } else {
            // No planning year — use current FAFSA cycle
            // After October 1, the next AY handbook is the one to reference
            startYear = today.getMonthValue() >= 10 ? today.getYear() + 1 : today.getYear();
        }
        return startYear + "-" + (startYear + 1);
    }

    /**
     * Fetches AVG Ch 3 (SAI / asset assessment) content for the given award year.
     * Returns formatted text ready for prompt injection, or a fallback URL note if Tavily unavailable.
     */
    public String fetchAssetRepositioningContent(String awardYear) {
        if (!tavilyClient.isLiveSearchConfigured()) {
            return fallback(awardYear, "avg", "ch3-student-aid-index-sai-and-pell-grant-eligibility",
                "AVG Ch 3 — Student Aid Index (SAI) & Asset Assessment");
        }
        String query = "FSA Handbook " + awardYear
            + " Application and Verification Guide Chapter 3 SAI Student Aid Index asset assessment parent student net worth";
        return fetchAndFormat(query, awardYear,
            "AVG Ch 3 — SAI Asset Assessment (" + awardYear + ")",
            "avg", "ch3-student-aid-index-sai-and-pell-grant-eligibility");
    }

    /**
     * Fetches Vol. 3 Ch. 5 (Professional Judgment / Special Circumstances) content.
     */
    public String fetchProfessionalJudgmentContent(String awardYear) {
        if (!tavilyClient.isLiveSearchConfigured()) {
            return fallback(awardYear, "vol3", "ch5-professional-judgment",
                "Vol. 3, Ch. 5 — Professional Judgment");
        }
        String query = "FSA Handbook " + awardYear
            + " Volume 3 Chapter 5 professional judgment special circumstances income loss adjustment";
        return fetchAndFormat(query, awardYear,
            "Vol. 3, Ch. 5 — Professional Judgment (" + awardYear + ")",
            "vol3", "ch5-professional-judgment");
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String fetchAndFormat(String query, String awardYear, String label,
                                   String volSlug, String chSlug) {
        try {
            List<Map<String, Object>> results = tavilyClient.searchHandbook(query, 3, FSA_DOMAINS, CONTENT_MAX_LEN);
            if (results.isEmpty()) {
                log.warn("FSA Handbook search returned no results for query: {}", query);
                return fallback(awardYear, volSlug, chSlug, label);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("=== LIVE FSA HANDBOOK CONTENT: ").append(label).append(" ===\n");
            for (Map<String, Object> r : results) {
                sb.append("\n[Source: ").append(r.get("url")).append("]\n");
                sb.append(r.get("content")).append("\n");
            }
            sb.append("=== END HANDBOOK CONTENT ===");
            return sb.toString();
        } catch (Exception e) {
            log.warn("FSA Handbook fetch failed, using fallback: {}", e.getMessage());
            return fallback(awardYear, volSlug, chSlug, label);
        }
    }

    private String fallback(String awardYear, String volSlug, String chSlug, String label) {
        String url = "https://fsapartners.ed.gov/knowledge-center/fsa-handbook/"
            + awardYear + "/federal-student-aid-handbook/" + volSlug + "-" + chSlug;
        return "=== FSA HANDBOOK REFERENCE (" + awardYear + ") ===\n"
            + label + "\n"
            + "Full text: " + url + "\n"
            + "Live content unavailable — reason from your training knowledge of this chapter and the URL above.\n"
            + "=== END HANDBOOK REFERENCE ===";
    }
}
