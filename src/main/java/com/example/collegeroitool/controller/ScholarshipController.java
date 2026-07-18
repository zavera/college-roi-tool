package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.ModelResponse;
import com.example.collegeroitool.model.Scholarship;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.repository.ScholarshipRepository;
import com.example.collegeroitool.service.AnthropicService;
import com.example.collegeroitool.service.AppConfigService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.MonthlyCostCapExceededException;
import com.example.collegeroitool.service.ScholarshipService;
import com.example.collegeroitool.service.SearchUsageService;
import com.example.collegeroitool.service.SubscriptionService;
import com.example.collegeroitool.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ScholarshipController.class);
    private static final int MAX_COMMENTS_LENGTH = 50;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private final ScholarshipService scholarshipService;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final SearchUsageService searchUsageService;
    private final AppConfigService appConfigService;
    private final ScholarshipRepository scholarshipRepository;
    private final ModelResponseRepository modelResponseRepository;
    private final AnthropicService anthropicService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScholarshipController(ScholarshipService scholarshipService, UserService userService,
                                  SubscriptionService subscriptionService, SearchUsageService searchUsageService,
                                  AppConfigService appConfigService, ScholarshipRepository scholarshipRepository,
                                  ModelResponseRepository modelResponseRepository, AnthropicService anthropicService) {
        this.scholarshipService = scholarshipService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.searchUsageService = searchUsageService;
        this.appConfigService = appConfigService;
        this.scholarshipRepository = scholarshipRepository;
        this.modelResponseRepository = modelResponseRepository;
        this.anthropicService = anthropicService;
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody Map<String, Object> body, Principal principal,
                                     HttpServletRequest httpRequest) {
        if (principal == null && !devBypass) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        AppUser user = resolveUser(principal);
        if (user != null) {
            SearchUsage usage = searchUsageService.getOrCreateForUser(user);
            if (!subscriptionService.hasAccess(user) && usage.getScholarship() >= appConfigService.getFreeSearchesLimit()) {
                return ResponseEntity.status(403).body(Map.of("error", "Free search limit reached for this tab"));
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> demographics = (Map<String, Object>) body.getOrDefault("demographics", Map.of());
        String comments = body.getOrDefault("comments", "").toString();
        if (comments.length() > MAX_COMMENTS_LENGTH) {
            comments = comments.substring(0, MAX_COMMENTS_LENGTH);
        }
        // Only one target school is supported — the UI enforces this too, but don't trust
        // the client; take at most the first one sent.
        @SuppressWarnings("unchecked")
        List<String> requestedSchools = (List<String>) body.getOrDefault("targetSchools", List.of());
        List<String> schools = requestedSchools.isEmpty()
            ? List.of() : List.of(requestedSchools.get(0));

        Scholarship entry = user != null ? persistInput(user, body) : null;

        String json;
        int status;
        try {
            json = scholarshipService.search(demographics, comments, schools,
                user != null ? user.getId() : null, httpRequest.getSession(true).getId());
            status = 200;
        } catch (MonthlyCostCapExceededException e) {
            if (entry != null) logModelResponse(entry, null, 429);
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("[scholarship] search failed userId={}: {}", user != null ? user.getId() : null, e.getMessage());
            json = null;
            status = 500;
        }

        if (entry != null) {
            logModelResponse(entry, json, status);
        }

        if (status == 500) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Scholarship search failed"));
        }

        if (user != null) {
            searchUsageService.incrementScholarship(user);
        }

        return ResponseEntity.ok(Map.of("scholarships", parseOrRaw(json)));
    }

    private Scholarship persistInput(AppUser user, Map<String, Object> body) {
        try {
            Scholarship entry = new Scholarship();
            entry.setUserId(user.getId());
            entry.setInputScholarshipPayload(objectMapper.writeValueAsString(body));
            return scholarshipRepository.save(entry);
        } catch (Exception e) {
            log.warn("[scholarship] failed to persist input userId={}: {}", user.getId(), e.getMessage());
            return null;
        }
    }

    private void logModelResponse(Scholarship entry, String outputPayload, int status) {
        ModelResponse resp = new ModelResponse();
        resp.setModelName(anthropicService.getScholarshipModel());
        resp.setTypeInputPayload(InputPayloadType.SCHOLARSHIP);
        resp.setInputId(entry.getId());
        resp.setOutputPayload(outputPayload);
        resp.setResponseStatus(status);
        resp.setPrompt(AnthropicService.SCHOLARSHIP_RECOMMENDATIONS_PROMPT_FILE);
        modelResponseRepository.save(resp);
    }

    /** Kept for backwards compatibility — delegates to unified search. */
    @PostMapping("/external")
    public ResponseEntity<?> searchExternal(@RequestBody Map<String, Object> body, Principal principal,
                                             HttpServletRequest httpRequest) {
        return search(body, principal, httpRequest);
    }

    /** Kept for backwards compatibility — delegates to unified search. */
    @PostMapping("/school-specific")
    public ResponseEntity<?> searchSchoolSpecific(@RequestBody Map<String, Object> body, Principal principal,
                                                   HttpServletRequest httpRequest) {
        return search(body, principal, httpRequest);
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
