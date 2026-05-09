package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.service.GroqService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<?> getAdvice(@RequestBody LlmAdviceRequest request) {
        try {
            String advice = groqService.getFinancialAdvice(request);
            // Try to parse as JSON — if successful, return structured data
            try {
                Object parsed = objectMapper.readValue(advice, Object.class);
                return ResponseEntity.ok(Map.of("data", parsed));
            } catch (Exception jsonEx) {
                // Not valid JSON — return as plain advice string (legacy fallback)
                return ResponseEntity.ok(Map.of("advice", advice));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not fetch advice: " + e.getMessage()));
        }
    }
}
