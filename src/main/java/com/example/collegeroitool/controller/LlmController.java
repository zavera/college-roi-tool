package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.service.FafsaAidPackageService;
import com.example.collegeroitool.service.GroqService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final GroqService groqService;
    private final FafsaAidPackageService aidPackageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmController(GroqService groqService, FafsaAidPackageService aidPackageService) {
        this.groqService = groqService;
        this.aidPackageService = aidPackageService;
    }

    @PostMapping("/advice")
    public ResponseEntity<?> getAdvice(@RequestBody LlmAdviceRequest request) {
        try {
            // Persist aid package if student context is provided
            if (request.getStudentId() != null && request.getAidYear() != null) {
                try { aidPackageService.upsert(request.getStudentId(), request.getAidYear(), request); }
                catch (Exception ignored) {}
            }
            String advice = groqService.getFinancialAdvice(request);
            try {
                Object parsed = objectMapper.readValue(advice, Object.class);
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
