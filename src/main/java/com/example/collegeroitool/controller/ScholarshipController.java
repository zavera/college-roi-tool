package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.service.AppConfigService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.ScholarshipService;
import com.example.collegeroitool.service.SearchUsageService;
import com.example.collegeroitool.service.SubscriptionService;
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
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final SearchUsageService searchUsageService;
    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScholarshipController(ScholarshipService scholarshipService, UserService userService,
                                  SubscriptionService subscriptionService, SearchUsageService searchUsageService,
                                  AppConfigService appConfigService) {
        this.scholarshipService = scholarshipService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.searchUsageService = searchUsageService;
        this.appConfigService = appConfigService;
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null && !devBypass) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        AppUser user = resolveUser(principal);
        if (user != null) {
            SearchUsage usage = searchUsageService.getOrCreateForUser(user);
            if (!subscriptionService.hasAccess(user) && usage.getScholarship() >= appConfigService.getFreeSearchesLimit()) {
                return ResponseEntity.status(403).body(Map.of("error", "Free search limit reached for this tab"));
            }
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> demographics = (Map<String, Object>) body.getOrDefault("demographics", Map.of());
            String comments = body.getOrDefault("comments", "").toString();
            @SuppressWarnings("unchecked")
            List<String> schools = (List<String>) body.getOrDefault("targetSchools", List.of());

            // TODO(follow-up): persist this search into the new `scholarship` input-log table
            // (see schema redesign plan) — deferred pending model_response wiring for this tab.

            String json = scholarshipService.search(demographics, comments, schools);

            if (user != null) {
                searchUsageService.incrementScholarship(user);
            }

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

    private AppUser resolveUser(Principal principal) {
        String email = resolveEmail(principal);
        if (email == null) return devBypass ? userService.findOrCreateDevUser() : null;
        return userService.findByEmail(email).orElse(null);
    }

    private String resolveEmail(Principal principal) {
        if (principal == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : principal.getName();
    }

    private Object parseOrRaw(String json) {
        try { return objectMapper.readValue(GroqService.stripMarkdownFences(json), Object.class); }
        catch (Exception e) { return json; }
    }
}
