package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.Coa;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.ModelResponse;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.repository.CoaRepository;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.service.AnthropicService;
import com.example.collegeroitool.service.AppConfigService;
import com.example.collegeroitool.service.AwardAssistService;
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
import java.util.Map;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmController(AwardAssistService awardAssistService, UserService userService,
                          SubscriptionService subscriptionService, SearchUsageService searchUsageService,
                          AppConfigService appConfigService, CoaRepository coaRepository,
                          ModelResponseRepository modelResponseRepository, AnthropicService anthropicService) {
        this.awardAssistService = awardAssistService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.searchUsageService = searchUsageService;
        this.appConfigService = appConfigService;
        this.coaRepository = coaRepository;
        this.modelResponseRepository = modelResponseRepository;
        this.anthropicService = anthropicService;
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
        int status;
        try {
            advice = awardAssistService.getFinancialAdvice(request,
                user != null ? user.getId() : null, httpRequest.getSession(true).getId());
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

        try {
            String json = GroqService.stripMarkdownFences(advice);
            Object parsed = objectMapper.readValue(json, Object.class);
            return ResponseEntity.ok(Map.of("data", parsed));
        } catch (Exception jsonEx) {
            return ResponseEntity.ok(Map.of("advice", advice));
        }
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
