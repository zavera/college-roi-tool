package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.ScholarshipService;
import com.example.collegeroitool.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scholarship")
public class ScholarshipController {

    private final ScholarshipService scholarshipService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScholarshipController(ScholarshipService scholarshipService, UserService userService) {
        this.scholarshipService = scholarshipService;
        this.userService = userService;
    }

    @PostMapping("/external")
    public ResponseEntity<?> searchExternal(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        try {
            Long studentId = body.get("studentId") != null ? Long.valueOf(body.get("studentId").toString()) : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> demographics = (Map<String, Object>) body.getOrDefault("demographics", Map.of());
            String comments = body.getOrDefault("comments", "").toString();
            String json = scholarshipService.searchExternal(studentId, demographics, comments);
            return ResponseEntity.ok(Map.of("scholarships", parseOrRaw(json)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/school-specific")
    public ResponseEntity<?> searchSchoolSpecific(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        try {
            Long studentId = body.get("studentId") != null ? Long.valueOf(body.get("studentId").toString()) : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> demographics = (Map<String, Object>) body.getOrDefault("demographics", Map.of());
            String comments = body.getOrDefault("comments", "").toString();
            @SuppressWarnings("unchecked")
            List<String> schools = (List<String>) body.getOrDefault("targetSchools", List.of());
            String json = scholarshipService.searchSchoolSpecific(studentId, demographics, comments, schools);
            return ResponseEntity.ok(Map.of("scholarships", parseOrRaw(json)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/timeline")
    public ResponseEntity<?> generateTimeline(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
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
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return json; }
    }
}
