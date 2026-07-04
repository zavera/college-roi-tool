package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.service.AppConfigService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.SearchUsageService;
import com.example.collegeroitool.service.SubscriptionService;
import com.example.collegeroitool.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final GroqService groqService;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final SearchUsageService searchUsageService;
    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmController(GroqService groqService, UserService userService,
                          SubscriptionService subscriptionService, SearchUsageService searchUsageService,
                          AppConfigService appConfigService) {
        this.groqService = groqService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.searchUsageService = searchUsageService;
        this.appConfigService = appConfigService;
    }

    @PostMapping("/advice")
    public ResponseEntity<?> getAdvice(@RequestBody LlmAdviceRequest request, Principal principal) {
        String email = resolveEmail(principal);
        AppUser user = email != null ? userService.findByEmail(email).orElse(null) : null;

        if (user != null) {
            SearchUsage usage = searchUsageService.getOrCreateForUser(user);
            if (!subscriptionService.hasAccess(user) && usage.getCoa() >= appConfigService.getFreeSearchesLimit()) {
                return ResponseEntity.status(403).body(Map.of("error", "Free search limit reached for this tab"));
            }
        }

        try {
            // TODO(follow-up): persist this request into the new `coa` input-log table
            // (see schema redesign plan) — deferred pending model_response wiring for this tab.
            String advice = groqService.getFinancialAdvice(request);

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
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not fetch advice: " + e.getMessage()));
        }
    }

    private String resolveEmail(Principal principal) {
        if (principal == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : principal.getName();
    }
}
