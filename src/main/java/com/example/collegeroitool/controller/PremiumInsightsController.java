package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.PremiumInsightsRequest;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/premium")
public class PremiumInsightsController {

    private final SubscriptionService subscriptionService;
    private final GroqService groqService;

    public PremiumInsightsController(SubscriptionService subscriptionService, GroqService groqService) {
        this.subscriptionService = subscriptionService;
        this.groqService = groqService;
    }

    @PostMapping("/insights")
    public ResponseEntity<?> getInsights(@RequestBody PremiumInsightsRequest req) {
        if (!subscriptionService.isActive()) {
            return ResponseEntity.status(403).body(Map.of("error", "Subscription required"));
        }
        try {
            String json = groqService.getPremiumInsights(req);
            // Return raw JSON string as-is — Groq returns valid JSON per prompt
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(json);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not generate insights: " + e.getMessage()));
        }
    }
}
