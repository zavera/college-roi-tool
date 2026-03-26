package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.service.GroqService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final GroqService groqService;

    public LlmController(GroqService groqService) {
        this.groqService = groqService;
    }

    @PostMapping("/advice")
    public ResponseEntity<Map<String, String>> getAdvice(@RequestBody LlmAdviceRequest request) {
        try {
            String advice = groqService.getFinancialAdvice(request);
            return ResponseEntity.ok(Map.of("advice", advice));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not fetch advice: " + e.getMessage()));
        }
    }
}