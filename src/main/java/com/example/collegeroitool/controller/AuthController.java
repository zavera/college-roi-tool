package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.MagicLinkService;
import com.example.collegeroitool.service.UserService;
import com.example.collegeroitool.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final UserService userService;
    private final UserSessionService sessionService;
    private final MagicLinkService magicLinkService;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public AuthController(UserService userService,
                          UserSessionService sessionService,
                          MagicLinkService magicLinkService) {
        this.userService      = userService;
        this.sessionService   = sessionService;
        this.magicLinkService = magicLinkService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String name     = body.getOrDefault("name", "").trim();
        String email    = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");

        if (name.isEmpty() || email.isEmpty() || password.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Name, email, and a password of at least 8 characters are required."));
        }
        try {
            AppUser user = userService.registerLocal(name, email, password);
            return ResponseEntity.ok(Map.of(
                "registered", true,
                "email", user.getEmail(),
                "name", user.getName()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (DataAccessException ex) {
            log.error("[register] DB error for email={}: {}", email, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "An account with this email already exists."));
        } catch (Exception ex) {
            log.error("[register] Unexpected error for email={}: {}", email, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Registration failed. Please try again."));
        }
    }

    /**
     * Returns current user info. Also registers this session so any previously active session
     * for the same user is invalidated (soft login-sharing prevention).
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(Principal principal, HttpServletRequest httpRequest) {
        if (devBypass && principal == null) {
            AppUser devUser = userService.findOrCreateDevUser();
            return ResponseEntity.ok(Map.of(
                "loggedIn",           true,
                "email",              "dev@local",
                "name",               "Dev User",
                "subscriptionActive", true,
                "institutionName",    "Callisto Tech"
            ));
        }
        if (principal == null) {
            log.warn("[/me] principal=null — no session or session expired");
            return ResponseEntity.status(401).body(Map.of("loggedIn", false));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email;
        String name;

        if (auth.getPrincipal() instanceof OAuth2User oAuth2User) {
            email = oAuth2User.getAttribute("email");
            name  = oAuth2User.getAttribute("name");
        } else {
            email = principal.getName();
            name  = email;
        }

        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("loggedIn", false));
        }

        AppUser user = userService.findByEmail(email).orElse(null);
        boolean subscribed = user != null && user.isSubscriptionActive();
        int searchCount           = user != null ? user.getSearchCount() : 0;
        int debtSearchCount       = user != null ? user.getDebtSearchCount() : 0;
        int fafsaUsageCount       = user != null ? user.getFafsaUsageCount() : 0;
        int scholarshipSearchCount = user != null ? user.getScholarshipSearchCount() : 0;
        if (user != null && user.getName() != null) name = user.getName();

        // Register this session — overwrites any prior session token for this user,
        // kicking out any other logged-in device for the same account.
        if (user != null) {
            try {
                HttpSession session = httpRequest.getSession(false);
                if (session != null) {
                    String ip = httpRequest.getHeader("X-Forwarded-For");
                    if (ip == null) ip = httpRequest.getRemoteAddr();
                    sessionService.registerLogin(user.getId(), session.getId(), ip);
                }
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok(Map.of(
            "loggedIn",                true,
            "email",                   email,
            "name",                    name != null ? name : email,
            "subscriptionActive",      subscribed,
            "searchCount",             searchCount,
            "debtSearchCount",         debtSearchCount,
            "fafsaUsageCount",         fafsaUsageCount,
            "scholarshipSearchCount",  scholarshipSearchCount,
            "institutionName",         "Callisto Tech"
        ));
    }

    @PostMapping("/search/increment")
    public ResponseEntity<?> incrementSearch(Principal principal) {
        if (devBypass && principal == null) return ResponseEntity.ok(Map.of("searchCount", 0));
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        String email = resolveEmail(principal);
        int count = userService.incrementSearchCount(email);
        return ResponseEntity.ok(Map.of("searchCount", count));
    }

    @PostMapping("/scholarship-search/increment")
    public ResponseEntity<?> incrementScholarshipSearch(Principal principal) {
        if (devBypass && principal == null) return ResponseEntity.ok(Map.of("scholarshipSearchCount", 0));
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        String email = resolveEmail(principal);
        int count = userService.incrementScholarshipSearchCount(email);
        return ResponseEntity.ok(Map.of("scholarshipSearchCount", count));
    }

    @PostMapping("/debt-search/increment")
    public ResponseEntity<?> incrementDebtSearch(Principal principal) {
        if (devBypass && principal == null) return ResponseEntity.ok(Map.of("debtSearchCount", 0));
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        String email = resolveEmail(principal);
        int count = userService.incrementDebtSearchCount(email);
        return ResponseEntity.ok(Map.of("debtSearchCount", count));
    }

    @PostMapping("/chat/increment")
    public ResponseEntity<?> incrementChatCount(Principal principal) {
        if (devBypass && principal == null) return ResponseEntity.ok(Map.of("chatCount", 1));
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        String email = resolveEmail(principal);
        int count = userService.incrementFafsaUsageCount(email);
        return ResponseEntity.ok(Map.of("chatCount", count));
    }

    @PostMapping("/subscription/toggle")
    public ResponseEntity<?> toggleSubscription(Principal principal) {
        if (devBypass && principal == null) return ResponseEntity.ok(Map.of("subscriptionActive", true));
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        String email = resolveEmail(principal);
        return userService.toggleSubscription(email)
            .<ResponseEntity<?>>map(active -> ResponseEntity.ok(Map.of("subscriptionActive", active)))
            .orElse(ResponseEntity.badRequest().body(Map.of("error", "User not found")));
    }

    @PostMapping("/admin/activate")
    public ResponseEntity<?> activateSubscription(
            @RequestParam String email,
            @RequestParam String adminKey) {
        String expectedKey = System.getenv().getOrDefault("ADMIN_KEY", "local-admin-secret");
        if (!expectedKey.equals(adminKey)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        boolean found = userService.activateSubscription(email);
        return found
            ? ResponseEntity.ok(Map.of("activated", true, "email", email))
            : ResponseEntity.badRequest().body(Map.of("error", "User not found: " + email));
    }

    // ── Magic link endpoints ──────────────────────────────────────────────────

    @PostMapping("/magic-link/request")
    public ResponseEntity<?> requestMagicLink(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required."));
        }
        try {
            magicLinkService.requestLink(email);
        } catch (RuntimeException ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
        // Always return success to avoid leaking whether the email is registered
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @GetMapping("/magic-link/verify")
    public void verifyMagicLink(@RequestParam String token,
                                HttpServletRequest request,
                                HttpServletResponse response) throws Exception {
        magicLinkService.consume(token).ifPresentOrElse(email -> {
            try {
                // Manually authenticate the user and persist to session
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(auth);
                SecurityContextHolder.setContext(ctx);

                HttpSession session = request.getSession(true);
                session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);

                log.info("[magic-link] verified and logged in email={}", email);
                response.sendRedirect("/app.html");
            } catch (Exception e) {
                log.error("[magic-link] session setup failed: {}", e.getMessage(), e);
                try { response.sendRedirect("/login.html?error=magic-link"); } catch (Exception ignored) {}
            }
        }, () -> {
            log.warn("[magic-link] invalid or expired token");
            try { response.sendRedirect("/login.html?error=magic-link"); } catch (Exception ignored) {}
        });
    }

    private String resolveEmail(Principal principal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : principal.getName();
    }
}
