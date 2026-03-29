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

    /** Generic chat endpoint — accepts {"prompt":"..."} and returns {"content":"..."}.
     *  Used by the College RoadMap feature to call Groq server-side. */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> body) {
        try {
            String prompt = body.get("prompt");
            if (prompt == null || prompt.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "prompt is required"));
            }
            String content = groqService.getCompletion(prompt);
            return ResponseEntity.ok(Map.of("content", content));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}