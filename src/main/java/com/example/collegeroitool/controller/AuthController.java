package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.UserService;
import org.springframework.beans.factory.annotation.Value;
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
        String email    = body.getOrDefault("email", "").trim();
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

        AppUser user = userService.findByEmail(email).orElse(null);
        boolean subscribed = user != null && user.isSubscriptionActive();
        if (user != null && user.getName() != null) name = user.getName();

        return ResponseEntity.ok(Map.of(
            "loggedIn",            true,
            "email",               email,
            "name",                name != null ? name : email,
            "subscriptionActive",  subscribed
        ));
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
