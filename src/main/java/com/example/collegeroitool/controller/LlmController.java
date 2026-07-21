package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.AwardAdviceResult;
import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.Coa;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.LowEarningSchoolEarnings;
import com.example.collegeroitool.model.ModelResponse;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.repository.CoaRepository;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.service.AnthropicService;
import com.example.collegeroitool.service.AppConfigService;
import com.example.collegeroitool.service.AwardAssistService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.LowEarningSchoolMatchService;
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
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private static final Logger log = LoggerFactory.getLogger(LlmController.class);

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private final AwardAssistService awardAssistService;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final SearchUsageService searchUsageService;
    private final AppConfigService appConfigService;
    private final CoaRepository coaRepository;
    private final ModelResponseRepository modelResponseRepository;
    private final AnthropicService anthropicService;
    private final LowEarningSchoolMatchService lowEarningSchoolMatchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmController(AwardAssistService awardAssistService, UserService userService,
                          SubscriptionService subscriptionService, SearchUsageService searchUsageService,
                          AppConfigService appConfigService, CoaRepository coaRepository,
                          ModelResponseRepository modelResponseRepository, AnthropicService anthropicService,
                          LowEarningSchoolMatchService lowEarningSchoolMatchService) {
        this.awardAssistService = awardAssistService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.searchUsageService = searchUsageService;
        this.appConfigService = appConfigService;
        this.coaRepository = coaRepository;
        this.modelResponseRepository = modelResponseRepository;
        this.anthropicService = anthropicService;
        this.lowEarningSchoolMatchService = lowEarningSchoolMatchService;
    }

    @PostMapping("/advice")
    public ResponseEntity<?> getAdvice(@RequestBody LlmAdviceRequest request, Principal principal,
                                        HttpServletRequest httpRequest) {
        AppUser user = resolveUser(principal);

        if (user != null) {
            SearchUsage usage = searchUsageService.getOrCreateForUser(user);
            if (!subscriptionService.hasAccess(user) && usage.getCoa() >= appConfigService.getFreeSearchesLimit()) {
                return ResponseEntity.status(403).body(Map.of("error", "Free search limit reached for this tab"));
            }
        }

        Coa entry = user != null ? persistInput(user, request) : null;

        String advice;
        LowEarningSchoolEarnings lowEarningData = null;
        int status;
        try {
            AwardAdviceResult result = awardAssistService.getFinancialAdvice(request,
                user != null ? user.getId() : null, httpRequest.getSession(true).getId());
            advice = result.getAdviceJson();
            lowEarningData = result.getLowEarningData();
            status = 200;
        } catch (MonthlyCostCapExceededException e) {
            if (entry != null) logModelResponse(entry, null, 429);
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("[llm-advice] Award Assist analysis failed userId={}: {}", user != null ? user.getId() : null, e.getMessage());
            advice = null;
            status = 500;
        }

        if (entry != null) {
            logModelResponse(entry, advice, status);
        }

        if (status == 500) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not fetch advice"));
        }

        if (user != null) {
            searchUsageService.incrementCoa(user);
        }

        Map<String, Object> lowEarningPayload = toLowEarningPayload(lowEarningData);

        try {
            String json = GroqService.stripMarkdownFences(advice);
            Object parsed = objectMapper.readValue(json, Object.class);
            return ResponseEntity.ok(Map.of("data", parsed, "lowEarningData", lowEarningPayload));
        } catch (Exception jsonEx) {
            return ResponseEntity.ok(Map.of("advice", advice, "lowEarningData", lowEarningPayload));
        }
    }

    /** Lightweight, standalone lookup so the FSA "Lower Earnings" flag can appear the moment a
     *  school is selected, instead of only after the full (20-30s) "Get AI Financial Summary"
     *  call. Uses the same LowEarningSchoolMatchService as getFinancialAdvice (exact match is
     *  free; the AI fallback only runs when the name isn't an exact match). Not gated behind the
     *  free-search-limit paywall and doesn't count against it — this is a cheap, deterministic
     *  lookup, not an AI-generated analysis. Fails open (empty result) on any error, including a
     *  monthly cost cap hit, so a background lookup never surfaces an error to the user. */
    @GetMapping("/low-earning")
    public ResponseEntity<?> getLowEarningData(@RequestParam String collegeName, Principal principal,
                                                HttpServletRequest httpRequest) {
        AppUser user = resolveUser(principal);
        try {
            Optional<LowEarningSchoolEarnings> match = lowEarningSchoolMatchService.match(
                collegeName, user != null ? user.getId() : null, httpRequest.getSession(true).getId());
            return ResponseEntity.ok(toLowEarningPayload(match.orElse(null)));
        } catch (Exception e) {
            log.warn("[low-earning] Lookup failed for collegeName={}: {}", collegeName, e.getMessage());
            return ResponseEntity.ok(Map.of());
        }
    }

    /** Raw FSA Earnings Data Report fields for the matched school, for deterministic display in
     *  the UI — never routed through the AI. Empty map (not null) when no school was matched, so
     *  the client can do a plain truthiness/keys check. */
    private Map<String, Object> toLowEarningPayload(LowEarningSchoolEarnings row) {
        if (row == null) return Map.of();
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("institutionName", row.getInstitutionName());
        payload.put("unitId", row.getUnitId());
        payload.put("state", row.getState());
        payload.put("earningsReported", row.getEarningsReported());
        payload.put("earningsInflationAdjusted", row.getEarningsInflationAdjusted());
        payload.put("hsEarningsThresholdValue", row.getHsEarningsThresholdValue());
        payload.put("hsEarningsThresholdType", row.getHsEarningsThresholdType());
        payload.put("lowerEarningsFlag", row.getLowerEarningsFlag());
        return payload;
    }

    private Coa persistInput(AppUser user, LlmAdviceRequest request) {
        try {
            Coa entry = new Coa();
            entry.setUserId(user.getId());
            entry.setInputCoaPayload(objectMapper.writeValueAsString(request));
            return coaRepository.save(entry);
        } catch (Exception e) {
            log.warn("[llm-advice] failed to persist input userId={}: {}", user.getId(), e.getMessage());
            return null;
        }
    }

    private void logModelResponse(Coa entry, String outputPayload, int status) {
        ModelResponse resp = new ModelResponse();
        resp.setModelName(anthropicService.getAwardAssistModel());
        resp.setTypeInputPayload(InputPayloadType.COA);
        resp.setInputId(entry.getId());
        resp.setOutputPayload(outputPayload);
        resp.setResponseStatus(status);
        resp.setPrompt(AnthropicService.AWARD_ASSIST_PROMPT_FILE);
        modelResponseRepository.save(resp);
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
