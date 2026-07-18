package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.Fafsa;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.ModelResponse;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.repository.FafsaRepository;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.service.AnthropicService;
import com.example.collegeroitool.service.AppConfigService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.MonthlyCostCapExceededException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/fafsa-prep")
public class FafsaPrepController {

    private static final Logger log = LoggerFactory.getLogger(FafsaPrepController.class);
    private static final String AWARD_YEAR = "2026-27";
    private static final Integer EXPECTED_TAX_YEAR = 2024; // PPY for the 2026-27 award year

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private final FafsaRepository fafsaRepository;
    private final ModelResponseRepository modelResponseRepository;
    private final UserService userService;
    private final AnthropicService anthropicService;
    private final SubscriptionService subscriptionService;
    private final SearchUsageService searchUsageService;
    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FafsaPrepController(FafsaRepository fafsaRepository,
                                ModelResponseRepository modelResponseRepository,
                                UserService userService,
                                AnthropicService anthropicService,
                                SubscriptionService subscriptionService,
                                SearchUsageService searchUsageService,
                                AppConfigService appConfigService) {
        this.fafsaRepository = fafsaRepository;
        this.modelResponseRepository = modelResponseRepository;
        this.userService = userService;
        this.anthropicService = anthropicService;
        this.subscriptionService = subscriptionService;
        this.searchUsageService = searchUsageService;
        this.appConfigService = appConfigService;
    }

    /** Which award-year rules the asset-repositioning analysis is currently grounded in.
     *  No student data — safe to call before login so the UI can show it up front. */
    @GetMapping("/rules-info")
    public ResponseEntity<?> rulesInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("awardYear", AWARD_YEAR);
        info.put("expectedTaxYear", EXPECTED_TAX_YEAR);
        info.put("sources", anthropicService.getHandbookSources(AWARD_YEAR));
        return ResponseEntity.ok(info);
    }

    /** List all FAFSA prep entries for the current user, newest first. */
    @GetMapping
    public ResponseEntity<?> list(Principal principal) {
        AppUser user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Map<String, Object>> rows = fafsaRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
            .stream().map(this::toHistoryMap).toList();
        return ResponseEntity.ok(rows);
    }

    /** Save a new FAFSA prep entry and run the asset-repositioning analysis. */
    @PostMapping
    public ResponseEntity<?> save(@RequestBody Map<String, Object> body, Principal principal,
                                   HttpServletRequest httpRequest) {
        AppUser user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        SearchUsage usage = searchUsageService.getOrCreateForUser(user);
        if (!subscriptionService.hasAccess(user) && usage.getFafsa() >= appConfigService.getFreeSearchesLimit()) {
            return ResponseEntity.status(403).body(Map.of("error", "Free search limit reached for this tab"));
        }

        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(body); }
        catch (Exception e) { payloadJson = "{}"; }

        Fafsa entry = new Fafsa();
        entry.setUserId(user.getId());
        entry.setInputFafsaPayload(payloadJson);
        entry = fafsaRepository.save(entry);

        String rawAnalysis = null;
        int status;
        Object parsedAnalysis = null;
        try {
            // This is a manual-entry planning tool with no document upload, so there is nothing
            // to cross-check tax years against. Pass matching expected/extracted years plus an
            // explicit note so the model doesn't flag a spurious "no tax year detected" discrepancy.
            rawAnalysis = anthropicService.getAssetRepositioningAdvice(payloadJson, AWARD_YEAR,
                EXPECTED_TAX_YEAR, EXPECTED_TAX_YEAR,
                "Data entered manually by the student/family, no tax documents were uploaded for this planning session. Do not flag a tax year discrepancy; set discrepancy to null.",
                user.getId(), httpRequest.getSession(true).getId());
            parsedAnalysis = objectMapper.readValue(GroqService.stripMarkdownFences(rawAnalysis), Object.class);
            status = 200;
        } catch (MonthlyCostCapExceededException e) {
            logModelResponse(entry, null, 429);
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // Keep whatever rawAnalysis the model actually returned (even if parsing it as JSON
            // failed) so the stored model_response row shows what really came back, not null.
            log.warn("[fafsa-prep] Claude analysis failed id={}: {}", entry.getId(), e.getMessage());
            status = 500;
        }
        logModelResponse(entry, rawAnalysis, status);
        if (status == 200) {
            searchUsageService.incrementFafsa(user);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", entry.getId());
        response.put("analysis", parsedAnalysis);
        return ResponseEntity.ok(response);
    }

    /** Delete a specific entry (only the owner can delete). */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Principal principal) {
        AppUser user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Optional<Fafsa> entry = fafsaRepository.findByIdAndUserId(id, user.getId());
        if (entry.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Not found"));
        fafsaRepository.delete(entry.get());
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void logModelResponse(Fafsa entry, String outputPayload, int status) {
        ModelResponse resp = new ModelResponse();
        resp.setModelName(anthropicService.getModel());
        resp.setTypeInputPayload(InputPayloadType.FAFSA);
        resp.setInputId(entry.getId());
        resp.setOutputPayload(outputPayload);
        resp.setResponseStatus(status);
        resp.setPrompt(AnthropicService.ASSET_REPOSITIONING_PROMPT_FILE);
        modelResponseRepository.save(resp);
    }

    private Map<String, Object> toHistoryMap(Fafsa entry) {
        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(entry.getInputFafsaPayload(), Map.class);
            payload = parsed;
        } catch (Exception e) {
            payload = Map.of();
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", entry.getId());
        m.put("label", payload.get("label"));
        m.put("addedBy", payload.get("addedBy"));
        m.put("createdAt", entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null);

        modelResponseRepository.findFirstByTypeInputPayloadAndInputIdOrderByCreatedAtDesc(InputPayloadType.FAFSA, entry.getId())
            .ifPresent(mr -> {
                try { m.put("analysis", objectMapper.readValue(mr.getOutputPayload(), Object.class)); }
                catch (Exception e) { m.put("analysis", null); }
            });
        return m;
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
}
