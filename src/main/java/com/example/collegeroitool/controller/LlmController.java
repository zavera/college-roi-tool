package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.service.FafsaAidPackageService;
import com.example.collegeroitool.service.GroqService;
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
    private final FafsaAidPackageService aidPackageService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmController(GroqService groqService, FafsaAidPackageService aidPackageService,
                         UserService userService) {
        this.groqService = groqService;
        this.aidPackageService = aidPackageService;
        this.userService = userService;
    }

    @PostMapping("/advice")
    public ResponseEntity<?> getAdvice(@RequestBody LlmAdviceRequest request, Principal principal) {
        try {
            // Persist aid package linked to the authenticated user — never trust client-supplied ID
            if (request.getAidYear() != null) {
                Long userId = resolveUserId();
                if (userId != null) {
                    try { aidPackageService.upsert(userId, request.getAidYear(), request); }
                    catch (Exception ignored) {}
                }
            }
            String advice = groqService.getFinancialAdvice(request);
            try {
                String json = advice.trim();
                if (json.startsWith("```")) {
                    json = json.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
                }
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

    private Long resolveUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            String email = (auth.getPrincipal() instanceof OAuth2User oAuth2User)
                ? (String) oAuth2User.getAttributes().get("email")
                : auth.getName();
            return userService.findByEmail(email).map(u -> u.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
