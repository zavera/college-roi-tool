package com.example.collegeroitool.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared Tavily web-search client, used by any feature needing live, display-cleaned search results. */
@Component
public class TavilySearchClient {

    @Value("${tavily.api.key}")
    private String apiKey;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public static final String DEV_STUB_KEY = "TAVILY_API_KEY_NOT_SET";
    public static final List<String> DEFAULT_EXCLUDED_DOMAINS = List.of(
        "youtube.com", "reddit.com", "quora.com", "facebook.com", "tiktok.com", "twitter.com", "x.com"
    );
    private static final String TAVILY_URL = "https://api.tavily.com/search";
    private static final int SNIPPET_MAX_LEN = 220;

    private final RestTemplate restTemplate;

    public TavilySearchClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        restTemplate = new RestTemplate(factory);
    }

    public boolean isLiveSearchConfigured() {
        return !(devBypass && DEV_STUB_KEY.equals(apiKey));
    }

    /** Runs a Tavily search, returning normalized {name, snippet, url, source: "live"} results, or empty on any failure. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String query, int maxResults, List<String> excludeDomains) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("api_key", apiKey);
            body.put("query", query);
            body.put("max_results", maxResults);
            body.put("search_depth", "basic");
            if (excludeDomains != null && !excludeDomains.isEmpty()) {
                body.put("exclude_domains", excludeDomains);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                TAVILY_URL, new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return List.of();

            List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
            if (results == null) return List.of();

            List<Map<String, Object>> parsed = new ArrayList<>();
            for (Map<String, Object> r : results) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", r.get("title"));
                item.put("snippet", cleanSnippet((String) r.get("content")));
                item.put("url", r.get("url"));
                item.put("source", "live");
                parsed.add(item);
            }
            return parsed;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Strip markdown artifacts/links and truncate raw search content to a display-friendly snippet. */
    private static String cleanSnippet(String raw) {
        if (raw == null) return "";
        String cleaned = raw
            .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")  // [text](url) -> text
            .replaceAll("[#*_`]", "")                          // markdown symbols
            .replaceAll("https?://\\S+", "")                   // bare URLs
            .replaceAll("\\s+", " ")
            .trim();
        if (cleaned.length() > SNIPPET_MAX_LEN) {
            cleaned = cleaned.substring(0, SNIPPET_MAX_LEN).trim() + "…";
        }
        return cleaned;
    }
}
