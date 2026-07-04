package com.example.collegeroitool.controller;

import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.ScholarshipService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scholarship")
public class ScholarshipController {

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private final ScholarshipService scholarshipService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScholarshipController(ScholarshipService scholarshipService) {
        this.scholarshipService = scholarshipService;
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null && !devBypass) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> demographics = (Map<String, Object>) body.getOrDefault("demographics", Map.of());
            String comments = body.getOrDefault("comments", "").toString();
            @SuppressWarnings("unchecked")
            List<String> schools = (List<String>) body.getOrDefault("targetSchools", List.of());

            // TODO(follow-up): persist this search into the new `scholarship` input-log table
            // (see schema redesign plan) — deferred pending the tab increment/model_response wiring.

            String json = scholarshipService.search(demographics, comments, schools);
            return ResponseEntity.ok(Map.of("scholarships", parseOrRaw(json)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Kept for backwards compatibility — delegates to unified search. */
    @PostMapping("/external")
    public ResponseEntity<?> searchExternal(@RequestBody Map<String, Object> body, Principal principal) {
        return search(body, principal);
    }

    /** Kept for backwards compatibility — delegates to unified search. */
    @PostMapping("/school-specific")
    public ResponseEntity<?> searchSchoolSpecific(@RequestBody Map<String, Object> body, Principal principal) {
        return search(body, principal);
    }

    @PostMapping("/timeline")
    public ResponseEntity<?> generateTimeline(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null && !devBypass) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        try {
            String selectedJson = objectMapper.writeValueAsString(
                body.getOrDefault("selectedScholarships", List.of()));
            String studentName = body.getOrDefault("studentName", "the student").toString();
            String json = scholarshipService.generateTimeline(selectedJson, studentName);
            return ResponseEntity.ok(Map.of("timeline", parseOrRaw(json)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private Object parseOrRaw(String json) {
        try { return objectMapper.readValue(GroqService.stripMarkdownFences(json), Object.class); }
        catch (Exception e) { return json; }
    }
}
