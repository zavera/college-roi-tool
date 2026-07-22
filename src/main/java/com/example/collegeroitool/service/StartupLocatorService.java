package com.example.collegeroitool.service;

import com.example.collegeroitool.model.YcStartupCatalog;
import com.example.collegeroitool.repository.YcStartupCatalogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Orchestrates the Startup Locator tab's personalized search. Two independent tracks feed
 *  Claude:
 *
 *  1. On-campus startups &amp; events — no structured dataset exists for "startups founded at
 *     this specific campus," so this stays live Tavily search, same as before.
 *  2. Nearby/interest-matched startups — deterministic seed catalog first
 *     ({@link YcStartupCatalogRepository}, ~4,200 active YC-backed companies filtered by
 *     industry/tag and location), then Tavily enriches only the matched candidates with
 *     freshness signals (recent funding news, hiring confirmation). This is the standard
 *     build-vs-buy split for this kind of feature: don't try to build a full startup database
 *     (Pitchbook/CB Insights charge $12k+/year for that); seed from a free structured source,
 *     enrich narrowly with live search.
 *
 *  Claude Haiku then formats/ranks/personalizes everything — including relating each seed-catalog
 *  match's industry/stage/hiring status to the student's stated major, never fabricating salary
 *  or ROI figures we don't actually have. */
@Service
public class StartupLocatorService {

    private static final List<String> EXCLUDED_GENERAL = List.of(
        "reddit.com", "quora.com", "yahoo.com", "pinterest.com"
    );
    private static final int RESULTS_PER_QUERY = 4;
    private static final int MAX_CATALOG_CANDIDATES = 6;

    /** Dropdown value -> keyword matched against the catalog's industry/subindustry/tags
     *  columns. Chosen to hit YC's actual controlled vocabulary (checked against the live
     *  dataset) while avoiding short substrings that false-positive inside unrelated words. */
    private static final Map<String, String> INTEREST_KEYWORDS = Map.of(
        "fintech", "fintech",
        "healthtech", "health",
        "ai", "artificial intelligence",
        "climate", "climate",
        "consumer", "consumer",
        "enterprise", "enterprise",
        "edtech", "education"
    );

    /** Full state name -> 2-letter postal abbreviation, used to build a bounded match pattern
     *  (", GA,") against all_locations rather than matching the bare state name — a bare
     *  "Georgia" substring match would also hit "Tbilisi, Georgia" (the country), "New York"
     *  would hit "New York City" fine but "Washington" would hit "Washington, D.C." even when
     *  the user meant the state, etc. The abbreviation is what YC's location strings actually
     *  use (e.g. "Atlanta, GA, USA"), so matching on it side-steps name collisions entirely. */
    private static final Map<String, String> US_STATE_ABBREVIATIONS = Map.ofEntries(
        Map.entry("alabama", "AL"), Map.entry("alaska", "AK"), Map.entry("arizona", "AZ"),
        Map.entry("arkansas", "AR"), Map.entry("california", "CA"), Map.entry("colorado", "CO"),
        Map.entry("connecticut", "CT"), Map.entry("delaware", "DE"), Map.entry("florida", "FL"),
        Map.entry("georgia", "GA"), Map.entry("hawaii", "HI"), Map.entry("idaho", "ID"),
        Map.entry("illinois", "IL"), Map.entry("indiana", "IN"), Map.entry("iowa", "IA"),
        Map.entry("kansas", "KS"), Map.entry("kentucky", "KY"), Map.entry("louisiana", "LA"),
        Map.entry("maine", "ME"), Map.entry("maryland", "MD"), Map.entry("massachusetts", "MA"),
        Map.entry("michigan", "MI"), Map.entry("minnesota", "MN"), Map.entry("mississippi", "MS"),
        Map.entry("missouri", "MO"), Map.entry("montana", "MT"), Map.entry("nebraska", "NE"),
        Map.entry("nevada", "NV"), Map.entry("new hampshire", "NH"), Map.entry("new jersey", "NJ"),
        Map.entry("new mexico", "NM"), Map.entry("new york", "NY"), Map.entry("north carolina", "NC"),
        Map.entry("north dakota", "ND"), Map.entry("ohio", "OH"), Map.entry("oklahoma", "OK"),
        Map.entry("oregon", "OR"), Map.entry("pennsylvania", "PA"), Map.entry("rhode island", "RI"),
        Map.entry("south carolina", "SC"), Map.entry("south dakota", "SD"), Map.entry("tennessee", "TN"),
        Map.entry("texas", "TX"), Map.entry("utah", "UT"), Map.entry("vermont", "VT"),
        Map.entry("virginia", "VA"), Map.entry("washington", "WA"), Map.entry("west virginia", "WV"),
        Map.entry("wisconsin", "WI"), Map.entry("wyoming", "WY")
    );

    private final TavilySearchClient tavilySearchClient;
    private final AnthropicService anthropicService;
    private final YcStartupCatalogRepository ycStartupCatalogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StartupLocatorService(TavilySearchClient tavilySearchClient, AnthropicService anthropicService,
                                  YcStartupCatalogRepository ycStartupCatalogRepository) {
        this.tavilySearchClient = tavilySearchClient;
        this.anthropicService = anthropicService;
        this.ycStartupCatalogRepository = ycStartupCatalogRepository;
    }

    public String search(String collegeName, String interestArea, String state, String major,
                          Double runwayMonths, Integer horizonMonths, String feasibilityLabel,
                          Long userId, String sessionId) {
        String college = collegeName != null && !collegeName.isBlank() ? collegeName : "this college";

        List<Map<String, Object>> onCampusResults = runSearches(buildCampusQueries(college, interestArea));
        String onCampusContent = formatForPrompt(onCampusResults);

        String riskTier = deriveRiskTier(runwayMonths, horizonMonths, feasibilityLabel);
        List<YcStartupCatalog> candidates = matchCatalog(interestArea, state, riskTier);
        String catalogContent = enrichAndFormatCatalog(candidates, riskTier);

        String financialContext = formatFinancialContext(runwayMonths, horizonMonths, feasibilityLabel);

        String aiJson = anthropicService.getStartupLocatorResults(
            college, interestArea, state, major, financialContext, onCampusContent, catalogContent, userId, sessionId);

        return attachLinks(aiJson, onCampusResults, candidates);
    }

    /** Attaches real, verified links to every result section — never trusts a URL from the AI
     *  response without checking it first. onCampusStartups/events: Claude may propose a "link"
     *  grounded in the on-campus live search results, but it's only kept if its domain actually
     *  appeared in those results (same anti-hallucination check ScholarshipService.validateLinks
     *  uses). nearbyCityStartups: the link is set deterministically from the seed catalog's own
     *  website/yc_url field by name match, overriding anything the AI proposed — that data is
     *  already verified real (it's the YC directory), so there's nothing to cross-check. */
    private String attachLinks(String aiJson, List<Map<String, Object>> onCampusResults,
                                List<YcStartupCatalog> catalogCandidates) {
        try {
            String cleaned = GroqService.stripMarkdownFences(aiJson);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(cleaned, Map.class);

            Set<String> onCampusDomains = new HashSet<>();
            for (Map<String, Object> r : onCampusResults) {
                String domain = domainOf(String.valueOf(r.getOrDefault("url", "")));
                if (domain != null) onCampusDomains.add(domain);
            }

            validateLinksInPlace(asItemList(result.get("onCampusStartups")), onCampusDomains);
            validateLinksInPlace(asItemList(result.get("events")), onCampusDomains);
            attachCatalogWebsites(asItemList(result.get("nearbyCityStartups")), catalogCandidates);

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            // Not parseable (e.g. truncated response) — return as-is; the controller's own
            // fallback handling deals with unparseable JSON.
            return aiJson;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asItemList(Object raw) {
        return raw instanceof List ? (List<Map<String, Object>>) raw : null;
    }

    private void validateLinksInPlace(List<Map<String, Object>> items, Set<String> verifiedDomains) {
        if (items == null) return;
        for (Map<String, Object> item : items) {
            Object link = item.get("link");
            String domain = link != null ? domainOf(link.toString()) : null;
            item.put("link", domain != null && verifiedDomains.contains(domain) ? link : null);
        }
    }

    private void attachCatalogWebsites(List<Map<String, Object>> items, List<YcStartupCatalog> candidates) {
        if (items == null) return;
        for (Map<String, Object> item : items) {
            Object nameObj = item.get("name");
            if (nameObj == null) continue;
            String name = nameObj.toString();
            candidates.stream()
                .filter(c -> c.getName() != null && c.getName().equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(c -> item.put("link", c.getWebsite() != null ? c.getWebsite() : c.getYcUrl()));
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

    /** Formats the client-computed runway/feasibility into a short context line for the prompt —
     *  the number itself was computed deterministically in app.html's computeStartupFeasibility,
     *  never recomputed or re-derived here or by the AI. */
    private String formatFinancialContext(Double runwayMonths, Integer horizonMonths, String feasibilityLabel) {
        if (runwayMonths == null && feasibilityLabel == null) return "Student did not provide financial feasibility inputs.";
        StringBuilder sb = new StringBuilder();
        if (runwayMonths != null) sb.append(String.format("Estimated runway: %.1f months. ", runwayMonths));
        else sb.append("Income covers expenses (no monthly burn). ");
        if (horizonMonths != null) sb.append("Student's stated time horizon: ").append(horizonMonths).append(" months. ");
        if (feasibilityLabel != null) sb.append("Feasibility assessment: ").append(feasibilityLabel).append(".");
        return sb.toString().trim();
    }

    /** Categorizes the student's soft financial input into a tier that biases both the catalog
     *  filter and the Tavily enrichment query — "tight" (short runway relative to their stated
     *  time horizon) leans the whole search toward hiring/paid-role signals; "comfortable" leans
     *  toward growth/equity signals; "unknown" (no financial inputs given) keeps the search
     *  generic. Derived from the client-computed feasibilityLabel text, never from re-deriving
     *  the runway math here. */
    private String deriveRiskTier(Double runwayMonths, Integer horizonMonths, String feasibilityLabel) {
        if (feasibilityLabel == null) return "unknown";
        String lower = feasibilityLabel.toLowerCase();
        if (lower.contains("high risk") || lower.contains("tight")) return "tight";
        if (lower.contains("feasible") || lower.contains("covers your costs")) return "comfortable";
        return "unknown";
    }

    private List<YcStartupCatalog> matchCatalog(String interestArea, String state, String riskTier) {
        String keyword = interestArea != null ? INTEREST_KEYWORDS.get(interestArea.toLowerCase().trim()) : null;
        String location = null;
        if (state != null && !state.isBlank()) {
            String abbr = US_STATE_ABBREVIATIONS.get(state.toLowerCase().trim());
            // Bounded pattern (", GA,") avoids matching the bare state name against unrelated
            // text (e.g. "Tbilisi, Georgia" the country, or "Washington, D.C.").
            location = abbr != null ? ", " + abbr + "," : null;
        }
        Pageable limit = PageRequest.of(0, MAX_CATALOG_CANDIDATES);

        // Tight runway: filter the catalog itself to currently-hiring companies first — a
        // stronger signal than just wording the enrichment query differently. Falls back to the
        // unfiltered match if that leaves nothing, so a real interest/location match never gets
        // dropped just because no candidate happens to be flagged hiring in the directory.
        if ("tight".equals(riskTier)) {
            List<YcStartupCatalog> hiringOnly = ycStartupCatalogRepository.search(keyword, location, true, limit);
            if (!hiringOnly.isEmpty()) return hiringOnly;
        }
        return ycStartupCatalogRepository.search(keyword, location, false, limit);
    }

    /** Runs one freshness-check Tavily query per matched candidate, bounded to
     *  MAX_CATALOG_CANDIDATES so this stays cheap regardless of catalog size. The query itself is
     *  worded differently depending on the student's soft financial risk tier — this is the
     *  actual "filtered search" step, not just query wording after the fact: a tight-runway
     *  student needs "can I get paid soon" signal, a comfortable one can explore "is this
     *  growing/well-funded" signal instead. */
    private String enrichAndFormatCatalog(List<YcStartupCatalog> candidates, String riskTier) {
        if (candidates.isEmpty()) return "(no seed-catalog matches for this interest area/location)";

        String enrichmentQuerySuffix = switch (riskTier) {
            case "tight" -> "hiring paid positions job openings entry-level 2026";
            case "comfortable" -> "funding round valuation growth news 2026";
            default -> "funding news hiring 2026";
        };

        StringBuilder sb = new StringBuilder();
        for (YcStartupCatalog c : candidates) {
            sb.append("- ").append(c.getName())
              .append(" | ").append(c.getOneLiner() != null ? c.getOneLiner() : "")
              .append(" | Industry: ").append(c.getIndustry() != null ? c.getIndustry() : "")
              .append(" | Location: ").append(c.getAllLocations() != null ? c.getAllLocations() : "")
              .append(" | Batch: ").append(c.getBatch() != null ? c.getBatch() : "")
              .append(" | Stage: ").append(c.getStage() != null ? c.getStage() : "")
              .append(" | Team size: ").append(c.getTeamSize() != null ? c.getTeamSize() : "unknown")
              .append(" | YC directory hiring flag: ").append(c.isHiring() ? "hiring" : "not flagged as hiring")
              .append(" | Website: ").append(c.getWebsite() != null ? c.getWebsite() : "");

            try {
                List<Map<String, Object>> enrichment = tavilySearchClient.search(
                    c.getName() + " " + enrichmentQuerySuffix, 2, EXCLUDED_GENERAL);
                if (!enrichment.isEmpty()) {
                    sb.append("\n  Recent live search signal: ");
                    for (Map<String, Object> r : enrichment) {
                        sb.append(r.getOrDefault("snippet", "")).append(" ");
                    }
                }
            } catch (Exception ignored) {}
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private List<String> buildCampusQueries(String collegeName, String interestArea) {
        List<String> queries = new ArrayList<>();
        boolean hasInterest = interestArea != null && !interestArea.isBlank() && !"any".equalsIgnoreCase(interestArea);

        queries.add(collegeName + " student startups founded alumni");
        queries.add(collegeName + " startup incubator accelerator program");
        queries.add(collegeName + " startup pitch competition demo day events");
        if (hasInterest) {
            queries.add(collegeName + " " + interestArea + " startups");
        }
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
