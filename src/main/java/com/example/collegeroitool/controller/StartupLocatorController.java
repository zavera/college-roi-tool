package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.service.AppConfigService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.MonthlyCostCapExceededException;
import com.example.collegeroitool.service.SearchUsageService;
import com.example.collegeroitool.service.StartupLocatorService;
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
@RequestMapping("/api/startup-locator")
public class StartupLocatorController {

    private static final Logger log = LoggerFactory.getLogger(StartupLocatorController.class);

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private final StartupLocatorService startupLocatorService;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final SearchUsageService searchUsageService;
    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StartupLocatorController(StartupLocatorService startupLocatorService, UserService userService,
                                     SubscriptionService subscriptionService, SearchUsageService searchUsageService,
                                     AppConfigService appConfigService) {
        this.startupLocatorService = startupLocatorService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.searchUsageService = searchUsageService;
        this.appConfigService = appConfigService;
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody Map<String, Object> body, Principal principal,
                                     HttpServletRequest httpRequest) {
        if (principal == null && !devBypass) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        AppUser user = resolveUser(principal);
        if (user != null) {
            SearchUsage usage = searchUsageService.getOrCreateForUser(user);
            if (!subscriptionService.hasAccess(user) && usage.getStartup() >= appConfigService.getFreeSearchesLimit()) {
                return ResponseEntity.status(403).body(Map.of("error", "Free search limit reached for this tab"));
            }
        }

        String collegeName = String.valueOf(body.getOrDefault("collegeName", "")).trim();
        String interestArea = String.valueOf(body.getOrDefault("interestArea", "")).trim();
        String state = String.valueOf(body.getOrDefault("state", "")).trim();
        String major = String.valueOf(body.getOrDefault("major", "")).trim();
        if (collegeName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "collegeName is required"));
        }

        // Client-computed runway/feasibility (deterministic math, done in app.html's
        // computeStartupFeasibility) — passed through as context for personalization only,
        // never recomputed here.
        Object runwayMonthsRaw = body.get("runwayMonths");
        Double runwayMonths = runwayMonthsRaw instanceof Number ? ((Number) runwayMonthsRaw).doubleValue() : null;
        Object horizonMonthsRaw = body.get("horizonMonths");
        Integer horizonMonths = horizonMonthsRaw instanceof Number ? ((Number) horizonMonthsRaw).intValue() : null;
        String feasibilityLabel = body.get("feasibilityLabel") != null ? String.valueOf(body.get("feasibilityLabel")) : null;

        String json;
        int status;
        try {
            json = startupLocatorService.search(collegeName, interestArea, state, major,
                runwayMonths, horizonMonths, feasibilityLabel,
                user != null ? user.getId() : null, httpRequest.getSession(true).getId());
            status = 200;
        } catch (MonthlyCostCapExceededException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("[startup-locator] search failed userId={}: {}", user != null ? user.getId() : null, e.getMessage());
            json = null;
            status = 500;
        }

        if (status == 500) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Startup search failed"));
        }

        if (user != null) {
            searchUsageService.incrementStartup(user);
        }

        try {
            String cleaned = GroqService.stripMarkdownFences(json);
            Object parsed = objectMapper.readValue(cleaned, Object.class);
            return ResponseEntity.ok(Map.of("data", parsed));
        } catch (Exception jsonEx) {
            return ResponseEntity.ok(Map.of("error", "Could not parse startup results"));
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
}
