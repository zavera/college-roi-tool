package com.example.collegeroitool.controller;

import com.example.collegeroitool.service.ScholarshipProfileService;
import com.example.collegeroitool.service.ScholarshipService;
import com.example.collegeroitool.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
    private final ScholarshipProfileService scholarshipProfileService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScholarshipController(ScholarshipService scholarshipService,
                                  ScholarshipProfileService scholarshipProfileService,
                                  UserService userService) {
        this.scholarshipService = scholarshipService;
        this.scholarshipProfileService = scholarshipProfileService;
        this.userService = userService;
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

            // Resolve userId from server-side session — never from client body
            Long userId = resolveUserId();
            if (userId != null) {
                try { scholarshipProfileService.save(userId, demographics, comments, schools); }
                catch (Exception ignored) {}
            }

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

    private Long resolveUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            String email = (auth.getPrincipal() instanceof OAuth2User oAuth2User)
                ? (String) oAuth2User.getAttributes().get("email")
                : auth.getName();
            return userService.findByEmail(email).map(u -> u.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Object parseOrRaw(String json) {
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return json; }
    }
}
