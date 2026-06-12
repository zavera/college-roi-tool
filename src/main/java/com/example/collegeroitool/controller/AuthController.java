package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** Register a new local account */
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
            return ResponseEntity.badRequest().body(Map.of(
                "error", "An account with this email already exists."));
        }
    }

    /** Current logged-in user info + subscription status */
    @GetMapping("/me")
    public ResponseEntity<?> me(Principal principal) {
        // Dev bypass: no real session — synthesise an active dev user
        if (devBypass && principal == null) {
            return ResponseEntity.ok(Map.of(
                "loggedIn",           true,
                "email",              "dev@local",
                "name",               "Dev User",
                "subscriptionActive", true
            ));
        }
        if (principal == null) {
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
        int searchCount = user != null ? user.getSearchCount() : 0;
        if (user != null && user.getName() != null) name = user.getName();

        return ResponseEntity.ok(Map.of(
            "loggedIn",            true,
            "email",               email,
            "name",                name != null ? name : email,
            "subscriptionActive",  subscribed,
            "searchCount",         searchCount
        ));
    }

    /** Increment search count for the calling user and return new count */
    @PostMapping("/search/increment")
    public ResponseEntity<?> incrementSearch(Principal principal) {
        if (devBypass && principal == null) {
            return ResponseEntity.ok(Map.of("searchCount", 0));
        }
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : principal.getName();

        int count = userService.incrementSearchCount(email);
        return ResponseEntity.ok(Map.of("searchCount", count));
    }

    /** Toggle the calling user's subscription on/off */
    @PostMapping("/subscription/toggle")
    public ResponseEntity<?> toggleSubscription(Principal principal) {
        if (devBypass && principal == null) {
            return ResponseEntity.ok(Map.of("subscriptionActive", true));
        }
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : principal.getName();

        return userService.toggleSubscription(email)
            .<ResponseEntity<?>>map(active -> ResponseEntity.ok(Map.of("subscriptionActive", active)))
            .orElse(ResponseEntity.badRequest().body(Map.of("error", "User not found")));
    }

    /** Admin endpoint — activate a subscription by email */
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
}
