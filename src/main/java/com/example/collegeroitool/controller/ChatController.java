package com.example.collegeroitool.controller;

import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.TavilySearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GroqService groqService;
    private final TavilySearchClient tavilySearchClient;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public ChatController(GroqService groqService, TavilySearchClient tavilySearchClient) {
        this.groqService = groqService;
        this.tavilySearchClient = tavilySearchClient;
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null && !devBypass) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        try {
            String message = (String) body.getOrDefault("message", "");
            if (message == null || message.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> history = (List<Map<String, Object>>) body.getOrDefault("history", List.of());

            // Live search: route to relevant domain based on question content
            String liveContent = fetchLiveContent(message);

            String answer = groqService.getAstraChatResponse(history, message, liveContent);
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not get a response. Please try again."));
        }
    }

    private String fetchLiveContent(String message) {
        String q = message.toLowerCase();
        List<String> domains;
        String query;

        if (q.contains("pslf") || q.contains("forgiveness") || q.contains("save") || q.contains("repayment") || q.contains("loan")) {
            domains = List.of("studentaid.gov");
            query = message + " site:studentaid.gov 2025";
        } else if (q.contains("scholarship") || q.contains("grant")) {
            domains = List.of("studentaid.gov", "scholarships.com");
            query = message + " scholarships 2025";
        } else if (q.contains("fafsa") || q.contains("financial aid") || q.contains("sai") || q.contains("efc")) {
            domains = List.of("studentaid.gov", "fsapartners.ed.gov");
            query = message + " site:studentaid.gov";
        } else {
            domains = List.of("studentaid.gov");
            query = message + " college financial aid 2025";
        }

        try {
            StringBuilder sb = new StringBuilder();
            var results = tavilySearchClient.searchHandbook(query, 3, domains, 800);
            for (var r : results) {
                sb.append("[Source: ").append(r.get("url")).append("]\n");
                sb.append(r.get("content")).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
