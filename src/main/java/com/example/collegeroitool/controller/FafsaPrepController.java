package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.FafsaProfile;
import com.example.collegeroitool.repository.FafsaProfileRepository;
import com.example.collegeroitool.service.AzureDocumentIntelligenceService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.TavilySearchClient;
import com.example.collegeroitool.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fafsa")
public class FafsaPrepController {

    private static final int FREE_FAFSA_USES = 3;

    private final FafsaProfileRepository fafsaProfileRepository;
    private final UserService userService;
    private final AzureDocumentIntelligenceService azureDocumentIntelligenceService;
    private final GroqService groqService;
    private final TavilySearchClient tavilySearchClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public FafsaPrepController(FafsaProfileRepository fafsaProfileRepository, UserService userService,
                                AzureDocumentIntelligenceService azureDocumentIntelligenceService,
                                GroqService groqService, TavilySearchClient tavilySearchClient) {
        this.fafsaProfileRepository = fafsaProfileRepository;
        this.userService = userService;
        this.azureDocumentIntelligenceService = azureDocumentIntelligenceService;
        this.groqService = groqService;
        this.tavilySearchClient = tavilySearchClient;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty()) return ResponseEntity.ok(Map.of("exists", false));
        return ResponseEntity.ok(toResponseMap(profileOpt.get(), true));
    }

    @PostMapping("/profile")
    public ResponseEntity<?> saveProfile(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        FafsaProfile profile = fafsaProfileRepository.findByUser_Email(user.getEmail()).orElseGet(() -> {
            FafsaProfile p = new FafsaProfile();
            p.setUser(user);
            return p;
        });

        profile.setStudentName((String) body.get("studentName"));
        Object dob = body.get("dateOfBirth");
        if (dob != null && !dob.toString().isBlank()) {
            try {
                profile.setDateOfBirth(LocalDate.parse(dob.toString()));
            } catch (Exception ignored) { /* leave unset if unparsable */ }
        }
        Object planningYear = body.get("planningYear");
        if (planningYear != null && !planningYear.toString().isBlank()) {
            try {
                profile.setPlanningYear(Integer.parseInt(planningYear.toString()));
            } catch (NumberFormatException ignored) { /* leave unset */ }
        }
        profile.setUpdatedAt(LocalDateTime.now());
        fafsaProfileRepository.save(profile);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    @PostMapping("/upload-tax-doc")
    public ResponseEntity<?> uploadTaxDoc(@RequestParam("file") MultipartFile file, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Save your demographics first."));
        }
        FafsaProfile profile = profileOpt.get();

        try {
            Map<String, String> extracted = azureDocumentIntelligenceService.extractKeyValuePairs(file);

            Map<String, Object> merged = profile.getExtractedDataJson() != null
                ? objectMapper.readValue(profile.getExtractedDataJson(), Map.class)
                : new LinkedHashMap<>();
            merged.put(file.getOriginalFilename(), extracted);
            profile.setExtractedDataJson(objectMapper.writeValueAsString(merged));
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(Map.of("filename", file.getOriginalFilename(), "extracted", extracted));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not extract document data: " + e.getMessage()));
        }
    }

    @PostMapping("/readiness-summary")
    public ResponseEntity<?> generateReadinessSummary(Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Save your demographics and upload a tax document first."));
        }
        FafsaProfile profile = profileOpt.get();

        try {
            String aiJson = groqService.getFafsaReadinessSummary(profile);
            Map<String, Object> summary = parseJsonOrWrap(aiJson, "readinessSummary");

            @SuppressWarnings("unchecked")
            List<String> scholarshipQueries = (List<String>) summary.getOrDefault("scholarshipQueries", List.of());
            List<Map<String, Object>> liveScholarships = new ArrayList<>();
            for (String query : scholarshipQueries) {
                liveScholarships.addAll(tavilySearchClient.search(query, 4, TavilySearchClient.DEFAULT_EXCLUDED_DOMAINS));
            }
            summary.put("liveScholarships", liveScholarships);

            String summaryJson = objectMapper.writeValueAsString(summary);
            profile.setReadinessSummaryJson(summaryJson);
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate readiness summary: " + e.getMessage()));
        }
    }

    @PostMapping("/roadmap")
    public ResponseEntity<?> generateRoadmap(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty() || profileOpt.get().getReadinessSummaryJson() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Generate your readiness summary first."));
        }
        FafsaProfile profile = profileOpt.get();

        try {
            Object selectedOptions = body.getOrDefault("selectedOptions", List.of());
            String selectedOptionsJson = objectMapper.writeValueAsString(selectedOptions);
            profile.setSelectedOptionsJson(selectedOptionsJson);

            Map<String, Object> existingSummary = objectMapper.readValue(profile.getReadinessSummaryJson(), Map.class);
            String deadlinesJson = objectMapper.writeValueAsString(existingSummary.getOrDefault("deadlines", List.of()));

            String aiJson = groqService.getFafsaRoadmap(profile, deadlinesJson, selectedOptionsJson);
            Map<String, Object> roadmap = parseJsonOrWrap(aiJson, "summary");

            profile.setRoadmapJson(objectMapper.writeValueAsString(roadmap));
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(roadmap);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate roadmap: " + e.getMessage()));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        // Always derived from the authenticated session above — never trust a client-supplied profile/user id.
        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Build your FAFSA Prep profile first."));
        }
        FafsaProfile profile = profileOpt.get();

        String question = (String) body.get("message");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required."));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());
        String historyText = history.stream()
            .map(m -> m.getOrDefault("role", "user") + ": " + m.getOrDefault("content", ""))
            .collect(Collectors.joining("\n"));

        try {
            String answer = groqService.getFafsaChatResponse(profile, historyText, question);
            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not get a response: " + e.getMessage()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Resolves the current user strictly from the authenticated session — never from request params/body. */
    private Optional<AppUser> resolveCurrentUser(Principal principal) {
        if (principal == null) {
            return devBypass ? Optional.of(userService.findOrCreateDevUser()) : Optional.empty();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : principal.getName();
        return email != null ? userService.findByEmail(email) : Optional.empty();
    }

    private boolean isUsageAllowed(AppUser user) {
        return user.isSubscriptionActive() || user.getFafsaUsageCount() < FREE_FAFSA_USES;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonOrWrap(String aiJson, String fallbackKey) {
        try {
            return objectMapper.readValue(aiJson, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>(Map.of(fallbackKey, aiJson));
        }
    }

    private Map<String, Object> toResponseMap(FafsaProfile profile, boolean exists) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("exists", exists);
        map.put("studentName", profile.getStudentName());
        map.put("dateOfBirth", profile.getDateOfBirth());
        map.put("planningYear", profile.getPlanningYear());
        map.put("extractedData", parseOrNull(profile.getExtractedDataJson()));
        map.put("readinessSummary", parseOrNull(profile.getReadinessSummaryJson()));
        map.put("selectedOptions", parseOrNull(profile.getSelectedOptionsJson()));
        map.put("roadmap", parseOrNull(profile.getRoadmapJson()));
        return map;
    }

    private Object parseOrNull(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
