package com.example.collegeroitool.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class CreditOfferSearchService {

    @Value("${tavily.api.key}")
    private String apiKey;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private static final String DEV_STUB_KEY = "TAVILY_API_KEY_NOT_SET";
    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private final RestTemplate restTemplate;

    public CreditOfferSearchService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        restTemplate = new RestTemplate(factory);
    }

    private static final Map<String, Integer> CREDIT_SCORE_MAP = Map.of(
        "below-580", 540, "580-619", 599, "620-659", 639,
        "660-699", 679, "700-739", 719, "740+", 780
    );

    // ── Fallback / dev-stub data — used when no live SerpAPI key is configured
    //    or when a live search fails or returns no results ──────────────────
    private static final List<Map<String, Object>> FALLBACK_BALANCE_TRANSFER_CARDS = List.of(
        cardOf("KeyPoint Credit Union Visa Classic", "KeyPoint Credit Union", "cu", 16, "0%", "None", "$0",
            620, 15000, "Open to all — join via Financial Fitness Association",
            "Longest 0% period with zero fees among credit unions",
            "https://www.kpcu.com/credit-cards"),
        cardOf("Fairwinds Cash Back Visa", "Fairwinds Credit Union", "cu", 12, "0%", "None", "$0",
            660, 15000, "Open to all — join via partner charity donation",
            "No fees of any kind — truly free balance transfer",
            "https://www.fairwinds.org/personal/credit-cards"),
        cardOf("Skyla Visa Platinum", "Skyla Credit Union", "cu", 12, "0%", "None", "$0",
            660, 15000, "Charlotte, NC area + select employers nationwide",
            "Zero fees; competitive ongoing APR after intro period",
            "https://www.skylacreditunion.com/personal/credit-cards"),
        cardOf("Navy Federal Platinum", "Navy Federal Credit Union", "cu", 12, "0.99%", "None", "$0",
            660, 15000, "Military members, veterans, DoD employees & family",
            "No transfer fee; low ongoing APR; strong member protections",
            "https://www.navyfederal.org/loans-cards/credit-cards/platinum"),
        cardOf("Wells Fargo Reflect® Card", "Wells Fargo", "bank", 21, "0%", "5% (min $5)", "$0",
            670, 15000, null,
            "Longest 0% intro period available — 21 months",
            "https://www.wellsfargo.com/credit-cards/reflect-card/"),
        cardOf("Citi Simplicity® Card", "Citi", "bank", 21, "0%", "5% (min $5)", "$0",
            670, 15000, null,
            "No late fees ever; 21-month 0% window",
            "https://www.citi.com/credit-cards/citi-simplicity-credit-card"),
        cardOf("Discover it® Balance Transfer", "Discover", "bank", 18, "0%", "3% (first year)", "$0",
            670, 15000, null,
            "Cashback match at end of year 1; lower transfer fee",
            "https://www.discover.com/credit-cards/balance-transfer/")
    );

    private static final List<Map<String, Object>> FALLBACK_REFI_LENDERS = List.of(
        lenderOf("SoFi", "3.99%", "15.99%", "4.39%", "15.99%", "5, 7, 10, 15, 20 yr", 5000, 650,
            "No fees · unemployment protection · career coaching",
            "https://www.sofi.com/refinance-student-loan/"),
        lenderOf("Earnest", "3.95%", "15.99%", "4.49%", "15.99%", "5–20 yr (custom to the month)", 5000, 650,
            "Skip one payment/year · fully flexible term length",
            "https://www.earnest.com/student-loan-refinancing"),
        lenderOf("Splash Financial", "4.99%", "10.99%", "4.99%", "10.99%", "5–25 yr", 5000, 640,
            "Marketplace — compares rates across multiple lenders at once",
            "https://www.splashfinancial.com/"),
        lenderOf("Laurel Road", "4.24%", "12.99%", "4.74%", "12.99%", "5, 7, 10, 15, 20 yr", 5000, 660,
            "Bonus rate discounts for healthcare & nursing professionals",
            "https://www.laurelroad.com/refinance-student-loans/"),
        lenderOf("ELFI", "4.86%", "8.49%", "4.98%", "8.26%", "5, 7, 10, 15, 20 yr", 10000, 680,
            "Dedicated personal loan advisor assigned to your account",
            "https://www.elfi.com/student-loan-refinancing/")
    );

    public int scoreForBand(String creditScoreBand) {
        return CREDIT_SCORE_MAP.getOrDefault(creditScoreBand, 0);
    }

    public Map<String, Object> findCreditOffers(int creditScore, double annualIncome, double privateBalance,
                                                 boolean liveSearchAllowed) {
        Map<String, Object> result = new HashMap<>();

        if (!liveSearchAllowed || (devBypass && DEV_STUB_KEY.equals(apiKey))) {
            result.put("balanceTransferCards", filterFallbackCards(creditScore, privateBalance));
            result.put("refiLenders", filterFallbackLenders(creditScore));
            result.put("source", liveSearchAllowed ? "fallback" : "fallback-paywalled");
            return result;
        }

        List<Map<String, Object>> btResults = searchLive(buildBalanceTransferQuery(creditScore));
        List<Map<String, Object>> refiResults = searchLive(buildRefiQuery(creditScore, annualIncome));

        if (btResults.isEmpty() && refiResults.isEmpty()) {
            result.put("balanceTransferCards", filterFallbackCards(creditScore, privateBalance));
            result.put("refiLenders", filterFallbackLenders(creditScore));
            result.put("source", "fallback");
            return result;
        }

        result.put("balanceTransferCards", btResults.isEmpty()
            ? filterFallbackCards(creditScore, privateBalance) : btResults);
        result.put("refiLenders", refiResults.isEmpty()
            ? filterFallbackLenders(creditScore) : refiResults);
        result.put("source", "live");
        return result;
    }

    private String buildBalanceTransferQuery(int creditScore) {
        String band = creditScore >= 700 ? "excellent credit" : "good credit";
        return "0% APR balance transfer credit card offers " + band + " 2026";
    }

    private String buildRefiQuery(int creditScore, double annualIncome) {
        String band = creditScore >= 700 ? "excellent credit" : "good credit";
        String incomeNote = annualIncome > 0 ? " income " + (long) annualIncome : "";
        return "student loan refinance rates " + band + incomeNote + " 2026";
    }

    private static final List<String> EXCLUDED_DOMAINS = List.of(
        "youtube.com", "reddit.com", "quora.com", "facebook.com", "tiktok.com", "twitter.com", "x.com"
    );

    private static final int SNIPPET_MAX_LEN = 220;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchLive(String query) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("api_key", apiKey);
            body.put("query", query);
            body.put("max_results", 5);
            body.put("search_depth", "basic");
            body.put("exclude_domains", EXCLUDED_DOMAINS);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                TAVILY_URL, new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return List.of();

            List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
            if (results == null) return List.of();

            List<Map<String, Object>> parsed = new ArrayList<>();
            for (Map<String, Object> r : results) {
                Map<String, Object> offer = new HashMap<>();
                offer.put("name", r.get("title"));
                offer.put("snippet", cleanSnippet((String) r.get("content")));
                offer.put("url", r.get("url"));
                offer.put("source", "live");
                parsed.add(offer);
            }
            return parsed;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Strip markdown artifacts/links and truncate raw search content to a display-friendly snippet. */
    private static String cleanSnippet(String raw) {
        if (raw == null) return "";
        String cleaned = raw
            .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")  // [text](url) -> text
            .replaceAll("[#*_`]", "")                          // markdown symbols
            .replaceAll("https?://\\S+", "")                   // bare URLs
            .replaceAll("\\s+", " ")
            .trim();
        if (cleaned.length() > SNIPPET_MAX_LEN) {
            cleaned = cleaned.substring(0, SNIPPET_MAX_LEN).trim() + "…";
        }
        return cleaned;
    }

    private List<Map<String, Object>> filterFallbackCards(int creditScore, double privateBalance) {
        if (privateBalance > 15000) return List.of();
        return FALLBACK_BALANCE_TRANSFER_CARDS.stream()
            .filter(c -> creditScore == 0 || creditScore >= (Integer) c.get("minCredit"))
            .toList();
    }

    private List<Map<String, Object>> filterFallbackLenders(int creditScore) {
        return FALLBACK_REFI_LENDERS.stream()
            .filter(l -> creditScore == 0 || creditScore >= (Integer) l.get("minCredit"))
            .toList();
    }

    private static Map<String, Object> cardOf(String name, String issuer, String type, int introPeriod,
            String introAPR, String transferFee, String annualFee, int minCredit, int maxBalance,
            String membership, String perk, String url) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", name);
        c.put("issuer", issuer);
        c.put("type", type);
        c.put("introPeriod", introPeriod);
        c.put("introAPR", introAPR);
        c.put("transferFee", transferFee);
        c.put("annualFee", annualFee);
        c.put("minCredit", minCredit);
        c.put("maxBalance", maxBalance);
        c.put("membership", membership);
        c.put("perk", perk);
        c.put("url", url);
        c.put("source", "fallback");
        return c;
    }

    private static Map<String, Object> lenderOf(String name, String fixedFrom, String fixedTo, String varFrom,
            String varTo, String terms, int minLoan, int minCredit, String perk, String url) {
        Map<String, Object> l = new HashMap<>();
        l.put("name", name);
        l.put("fixedFrom", fixedFrom);
        l.put("fixedTo", fixedTo);
        l.put("varFrom", varFrom);
        l.put("varTo", varTo);
        l.put("terms", terms);
        l.put("minLoan", minLoan);
        l.put("minCredit", minCredit);
        l.put("perk", perk);
        l.put("url", url);
        l.put("source", "fallback");
        return l;
    }
}
