package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.DebtIntakeRequest;
import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.CreditOfferSearchService;
import com.example.collegeroitool.service.DebtManagementService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debt")
public class DebtManagementController {

    private static final int FREE_LIVE_SEARCHES = 3;

    private final DebtManagementService debtService;
    private final GroqService groqService;
    private final CreditOfferSearchService creditOfferSearchService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public DebtManagementController(DebtManagementService debtService, GroqService groqService,
                                     CreditOfferSearchService creditOfferSearchService, UserService userService) {
        this.debtService = debtService;
        this.groqService = groqService;
        this.creditOfferSearchService = creditOfferSearchService;
        this.userService = userService;
    }

    @PostMapping("/repayment-plans")
    public ResponseEntity<?> getRepaymentPlans(@RequestBody DebtIntakeRequest req) {
        try {
            List<Map<String, Object>> plans = debtService.calculateRepaymentPlans(req);
            Map<String, Object> pslfResult = null;
            if (req.getEmployerName() != null && !req.getEmployerName().isBlank()) {
                pslfResult = debtService.checkPslfEligibility(req.getEmployerName());
            }
            String aiJson = groqService.getRepaymentRecommendation(req, plans, pslfResult);
            Object aiParsed;
            try {
                aiParsed = objectMapper.readValue(aiJson, Object.class);
            } catch (Exception e) {
                aiParsed = Map.of("rationale", aiJson);
            }
            return ResponseEntity.ok(Map.of(
                "plans",      plans,
                "pslf",       pslfResult != null ? pslfResult : Map.of(),
                "aiAdvice",   aiParsed
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not calculate repayment plans: " + e.getMessage()));
        }
    }

    @PostMapping("/credit-offers")
    public ResponseEntity<?> getCreditOffers(@RequestBody DebtIntakeRequest req, Principal principal) {
        try {
            int creditScore = creditOfferSearchService.scoreForBand(req.getCreditScoreBand());
            double income = req.getAnnualGrossIncome() != null ? req.getAnnualGrossIncome() : 0;
            double privateBalance = req.getPrivateLoanBalance() != null ? req.getPrivateLoanBalance() : 0;

            boolean liveSearchAllowed = isLiveSearchAllowed(principal);
            Map<String, Object> offers = creditOfferSearchService.findCreditOffers(
                creditScore, income, privateBalance, liveSearchAllowed);
            return ResponseEntity.ok(offers);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not fetch credit offers: " + e.getMessage()));
        }
    }

    /** Live (paid) search is capped at FREE_LIVE_SEARCHES per user unless their subscription is active. */
    private boolean isLiveSearchAllowed(Principal principal) {
        if (devBypass && principal == null) return true;
        if (principal == null) return false;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : principal.getName();
        if (email == null) return false;

        AppUser user = userService.findByEmail(email).orElse(null);
        if (user == null) return false;
        return user.isSubscriptionActive() || user.getDebtSearchCount() < FREE_LIVE_SEARCHES;
    }

    @PostMapping("/pslf-check")
    public ResponseEntity<?> checkPslf(@RequestBody Map<String, String> body) {
        try {
            String employer = body.get("employerName");
            return ResponseEntity.ok(debtService.checkPslfEligibility(employer));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "PSLF check failed: " + e.getMessage()));
        }
    }

    @PostMapping("/hardship-letter")
    public ResponseEntity<?> generateHardshipLetter(@RequestBody DebtIntakeRequest req) {
        try {
            String letter = groqService.getHardshipLetter(req);
            // Split letter and checklist
            String letterText   = letter;
            String checklistText = "";
            int splitIdx = letter.indexOf("---CHECKLIST---");
            if (splitIdx >= 0) {
                letterText   = letter.substring(0, splitIdx).trim();
                checklistText = letter.substring(splitIdx + 15).trim();
            }
            return ResponseEntity.ok(Map.of(
                "letter",    letterText,
                "checklist", checklistText
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate hardship letter: " + e.getMessage()));
        }
    }
}
