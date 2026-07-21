package com.example.collegeroitool.service;

import com.example.collegeroitool.dto.AwardAdviceResult;
import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.model.LowEarningSchoolEarnings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Orchestrates Award Assist's "AI Financial Summary" — live Tavily search for school/major
 *  specific facts (clubs, scholarships, resources, jobs), then Claude Haiku turns that into
 *  the structured content the UI renders. The Financial Gap Analysis, Student Profile, Key
 *  Metrics, and repayment-scenario tables shown alongside this are all deterministic client-
 *  side math (see app.html renderCollegeSection/recalculateGap) — this service and the AI have
 *  no part in them, and nothing here should try to recompute those figures. The FSA "Lower
 *  Earnings" flag/figures resolved via LowEarningSchoolMatchService follow the same rule: the
 *  raw row is returned as-is for deterministic display, and only its flag value is handed to
 *  the AI call as context to explain, never to verify or recompute. */
@Service
public class AwardAssistService {

    private static final List<String> EXCLUDED_GENERAL = List.of(
        "reddit.com", "quora.com", "yahoo.com", "pinterest.com"
    );
    private static final int RESULTS_PER_QUERY = 4;

    private final TavilySearchClient tavilySearchClient;
    private final AnthropicService anthropicService;
    private final LowEarningSchoolMatchService lowEarningSchoolMatchService;

    public AwardAssistService(TavilySearchClient tavilySearchClient, AnthropicService anthropicService,
                               LowEarningSchoolMatchService lowEarningSchoolMatchService) {
        this.tavilySearchClient = tavilySearchClient;
        this.anthropicService = anthropicService;
        this.lowEarningSchoolMatchService = lowEarningSchoolMatchService;
    }

    public AwardAdviceResult getFinancialAdvice(LlmAdviceRequest req, Long userId, String sessionId) {
        String collegeName = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major       = req.getMajor()       != null ? req.getMajor()       : "Undecided";

        List<Map<String, Object>> rawResults = runSearches(buildQueries(collegeName, major));
        String liveSearchContent = formatForPrompt(rawResults);

        Optional<LowEarningSchoolEarnings> lowEarning =
            lowEarningSchoolMatchService.match(collegeName, userId, sessionId);

        String adviceJson = anthropicService.getFinancialAdvice(
            req, liveSearchContent, lowEarning.orElse(null), userId, sessionId);

        return new AwardAdviceResult(adviceJson, lowEarning.orElse(null));
    }

    private List<String> buildQueries(String collegeName, String major) {
        List<String> queries = new ArrayList<>();
        queries.add(collegeName + " " + major + " professional societies student clubs");
        queries.add(collegeName + " first year experience office financial aid appeal emergency fund");
        queries.add(collegeName + " " + major + " scholarships department awards");
        queries.add(collegeName + " " + major + " internship co-op research assistant jobs");
        return queries;
    }

    private List<Map<String, Object>> runSearches(List<String> queries) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (String q : queries) {
            try {
                all.addAll(tavilySearchClient.search(q, RESULTS_PER_QUERY, EXCLUDED_GENERAL));
            } catch (Exception ignored) {}
        }
        // Deduplicate by URL
        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> r : all) {
            String url = String.valueOf(r.getOrDefault("url", ""));
            deduped.putIfAbsent(url, r);
        }
        return new ArrayList<>(deduped.values());
    }

    private String formatForPrompt(List<Map<String, Object>> results) {
        if (results.isEmpty()) return "(no live search results found)";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : results) {
            sb.append("- ").append(r.getOrDefault("name", "")).append(": ")
              .append(r.getOrDefault("snippet", "")).append(" [")
              .append(r.getOrDefault("url", "")).append("]\n");
        }
        return sb.toString().trim();
    }
}
