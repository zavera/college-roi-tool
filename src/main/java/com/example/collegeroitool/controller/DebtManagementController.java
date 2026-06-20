package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.DebtIntakeRequest;
import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.CreditOfferSearchService;
import com.example.collegeroitool.service.DebtManagementService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.PostGradProfileService;
import com.example.collegeroitool.service.TavilySearchClient;
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
    private final TavilySearchClient tavilySearchClient;
    private final PostGradProfileService postGradProfileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> STUDENTAID_DOMAINS = List.of("studentaid.gov", "consumerfinance.gov");
    private static final List<String> GENERAL_DOMAINS    = List.of();

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public DebtManagementController(DebtManagementService debtService, GroqService groqService,
                                     CreditOfferSearchService creditOfferSearchService,
                                     UserService userService, TavilySearchClient tavilySearchClient,
                                     PostGradProfileService postGradProfileService) {
        this.debtService = debtService;
        this.groqService = groqService;
        this.creditOfferSearchService = creditOfferSearchService;
        this.userService = userService;
        this.tavilySearchClient = tavilySearchClient;
        this.postGradProfileService = postGradProfileService;
    }

    /** Fetches live studentaid.gov content relevant to the given query for prompt injection. */
    private String fetchLiveDebtContent(String... queries) {
        StringBuilder sb = new StringBuilder();
        for (String q : queries) {
            try {
                var results = tavilySearchClient.searchHandbook(q, 2, STUDENTAID_DOMAINS, 900);
                for (var r : results) {
                    sb.append("\n[Source: ").append(r.get("url")).append("]\n");
                    sb.append(r.get("content")).append("\n");
                }
            } catch (Exception ignored) {}
        }
        String content = sb.toString().trim();
        return content.isEmpty()
            ? "(Live search unavailable — reason from training knowledge of studentaid.gov)"
            : content;
    }

    @PostMapping("/repayment-plans")
    public ResponseEntity<?> getRepaymentPlans(@RequestBody DebtIntakeRequest req, Principal principal) {
        try {
            // Persist post-grad profile linked to the authenticated user — never trust client-supplied ID
            Long userId = resolveUserId(principal);
            if (userId != null) {
                try { postGradProfileService.save(userId, req); }
                catch (Exception ignored) {}
            }
            List<Map<String, Object>> plans = debtService.calculateRepaymentPlans(req);
            Map<String, Object> pslfResult = null;
            if (req.getEmployerName() != null && !req.getEmployerName().isBlank()) {
                pslfResult = debtService.checkPslfEligibility(req.getEmployerName());
            }
            String liveContent = fetchLiveDebtContent(
                "SAVE IDR income-driven repayment plan 2025 student loan site:studentaid.gov",
                "PSLF public service loan forgiveness qualifying payments 2025 site:studentaid.gov"
            );
            String aiJson = groqService.getRepaymentRecommendation(req, plans, pslfResult, liveContent);
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
        AppUser user = userService.findByEmail(resolveEmail(principal)).orElse(null);
        if (user == null) return false;
        return user.isSubscriptionActive() || user.getDebtSearchCount() < FREE_LIVE_SEARCHES;
    }

    private String resolveEmail(Principal principal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : (principal != null ? principal.getName() : null);
    }

    private Long resolveUserId(Principal principal) {
        try {
            String email = resolveEmail(principal);
            if (email == null) return null;
            return userService.findByEmail(email).map(u -> u.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Fetches live web content about a private lender's hardship, repayment, and loan agreement pages. */
    private Map<String, Object> fetchPrivateLenderResearch(String lender) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            var forbearance = tavilySearchClient.searchHandbook(
                lender + " student loan forbearance hardship relief help center 2025", 3, GENERAL_DOMAINS, 1000);
            result.put("forbearanceResults", forbearance);
        } catch (Exception ignored) { result.put("forbearanceResults", List.of()); }
        try {
            var faq = tavilySearchClient.searchHandbook(
                lender + " student loan repayment assistance FAQ hardship options 2025", 3, GENERAL_DOMAINS, 1000);
            result.put("faqResults", faq);
        } catch (Exception ignored) { result.put("faqResults", List.of()); }
        try {
            var terms = tavilySearchClient.searchHandbook(
                lender + " student loan promissory note loan agreement repayment terms conditions", 2, GENERAL_DOMAINS, 800);
            result.put("loanTermsResults", terms);
        } catch (Exception ignored) { result.put("loanTermsResults", List.of()); }
        return result;
    }

    @PostMapping("/private-lender-info")
    public ResponseEntity<?> getPrivateLenderInfo(@RequestBody DebtIntakeRequest req) {
        String lender = req.getPrivateLender();
        if (lender == null || lender.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Private lender name is required."));
        }
        try {
            Map<String, Object> research = fetchPrivateLenderResearch(lender);
            return ResponseEntity.ok(research);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not fetch private lender info: " + e.getMessage()));
        }
    }

    @PostMapping("/private-hardship-letter")
    public ResponseEntity<?> generatePrivateHardshipLetter(@RequestBody DebtIntakeRequest req) {
        String lender = req.getPrivateLender();
        if (lender == null || lender.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Private lender name is required."));
        }
        try {
            Map<String, Object> research = fetchPrivateLenderResearch(lender);
            // Flatten all live research into a single prompt-injection block
            StringBuilder liveContent = new StringBuilder();
            for (String key : List.of("forbearanceResults", "faqResults", "loanTermsResults")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) research.get(key);
                if (items != null) {
                    for (var item : items) {
                        liveContent.append("\n[Source: ").append(item.get("url")).append("]\n");
                        Object content = item.get("content");
                        if (content == null) content = item.get("snippet");
                        liveContent.append(content).append("\n");
                    }
                }
            }
            String live = liveContent.toString().trim();
            if (live.isEmpty()) live = "(Live search unavailable — use lender website for current forbearance policies)";

            String letter = groqService.getPrivateHardshipLetter(req, live);
            String letterText    = letter;
            String checklistText = "";
            int splitIdx = letter.indexOf("---CHECKLIST---");
            if (splitIdx >= 0) {
                letterText    = letter.substring(0, splitIdx).trim();
                checklistText = letter.substring(splitIdx + 15).trim();
            }
            return ResponseEntity.ok(Map.of(
                "letter",    letterText,
                "checklist", checklistText
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate private hardship letter: " + e.getMessage()));
        }
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
            String hardshipType = req.getHardshipType() != null ? req.getHardshipType() : "general";
            String servicer = req.getLoanServicer() != null ? req.getLoanServicer() : "";
            String liveContent = fetchLiveDebtContent(
                "student loan " + hardshipType + " deferment forbearance requirements 2025 site:studentaid.gov",
                (servicer.isBlank() ? "" : servicer + " loan servicer hardship forbearance contact")
                    .strip().isEmpty() ? "federal student loan forbearance deferment how to apply site:studentaid.gov"
                    : servicer + " student loan hardship forbearance deferment 2025"
            );
            String letter = groqService.getHardshipLetter(req, liveContent);
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
