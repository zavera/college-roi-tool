package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.service.GroqService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final GroqService groqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmController(GroqService groqService) {
        this.groqService = groqService;
    }

    @PostMapping("/advice")
    public ResponseEntity<?> getAdvice(@RequestBody LlmAdviceRequest request, Principal principal) {
        try {
            // TODO(follow-up): persist this request into the new `coa` input-log table
            // (see schema redesign plan) — deferred pending the tab increment/model_response wiring.
            String advice = groqService.getFinancialAdvice(request);
            try {
                String json = GroqService.stripMarkdownFences(advice);
                Object parsed = objectMapper.readValue(json, Object.class);
                return ResponseEntity.ok(Map.of("data", parsed));
            } catch (Exception jsonEx) {
                return ResponseEntity.ok(Map.of("advice", advice));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not fetch advice: " + e.getMessage()));
        }
    }

}
