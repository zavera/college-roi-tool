package com.example.collegeroitool.service;

import com.example.collegeroitool.dto.DebtIntakeRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class DebtManagementService {

    // 2025 Federal Poverty Guidelines (48 contiguous states + DC)
    private static final double POVERTY_BASE  = 15060.0;
    private static final double POVERTY_INCR  = 5380.0;

    // Government keywords that automatically qualify for PSLF
    private static final List<String> GOV_KEYWORDS = List.of(
        "federal", "government", "state ", "county", "city of", "town of",
        "township", "municipal", "department of", "dept of", "public school",
        "unified school", "school district", "community college", "state university",
        "state college", "public university", "us army", "u.s. army", "us navy",
        "u.s. navy", "us air force", "u.s. air force", "us marine", "u.s. marine",
        "national guard", "irs ", "fbi ", "cia ", "doj ", "dod ", "va ", "hud "
    );

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Repayment Plan Calculator ─────────────────────────────────────────────

    public List<Map<String, Object>> calculateRepaymentPlans(DebtIntakeRequest req) {
        double principal   = req.getFederalLoanBalance() != null ? req.getFederalLoanBalance() : 0;
        double agi         = req.getAnnualGrossIncome()  != null ? req.getAnnualGrossIncome()  : 0;
        int    household   = req.getHouseholdSize()      != null ? req.getHouseholdSize()      : 1;
        double annualRate  = req.getInterestRate()       != null ? req.getInterestRate() / 100 : 0.0653;
        double monthlyRate = annualRate / 12;

        double povertyLine = POVERTY_BASE + (Math.max(household, 1) - 1) * POVERTY_INCR;

        List<Map<String, Object>> plans = new ArrayList<>();

        // Standard (10-year fixed)
        double stdPayment = amortizedPayment(principal, monthlyRate, 120);
        plans.add(plan("Standard Repayment",
            stdPayment,
            stdPayment * 120,
            0, 10,
            "Fixed payments over 10 years. Highest monthly payment, least total interest paid.",
            "borrowers who can afford higher payments and want to pay off debt quickly"));

        // Graduated (10-year, starts low, increases every 2 years)
        double gradStart = stdPayment * 0.6;
        double gradTotal = gradPaymentTotal(principal, monthlyRate);
        plans.add(plan("Graduated Repayment",
            gradStart,
            gradTotal,
            0, 10,
            "Payments start low and increase every 2 years. Good if you expect income to grow.",
            "recent graduates expecting salary increases in the near term"));

        // Extended (25-year fixed) — requires balance > $30,000
        if (principal >= 30000) {
            double extPayment = amortizedPayment(principal, monthlyRate, 300);
            plans.add(plan("Extended Repayment",
                extPayment,
                extPayment * 300,
                0, 25,
                "Lower monthly payments spread over 25 years. More total interest paid overall.",
                "borrowers with over $30,000 in federal debt who need a lower monthly payment"));
        }

        // SAVE (formerly REPAYE) — 5% of discretionary income above 225% poverty line
        double saveDiscretionary = Math.max(0, agi - (povertyLine * 2.25));
        double savePayment = saveDiscretionary * 0.05 / 12;
        int    saveYears   = principal <= 12000 ? 10 : (principal <= 28000 ? 15 : 20);
        plans.add(plan("SAVE (formerly REPAYE)",
            savePayment,
            estimateIDRTotal(principal, monthlyRate, savePayment, saveYears * 12),
            estimateIDRForgiveness(principal, monthlyRate, savePayment, saveYears * 12),
            saveYears,
            "Lowest payment floor of all IDR plans. Unpaid interest does not capitalize on principal.",
            "borrowers with high debt relative to income, especially those pursuing PSLF"));

        // PAYE — 10% of discretionary above 150% poverty line, cap at standard payment
        double payeDiscretionary = Math.max(0, agi - (povertyLine * 1.5));
        double payePayment = Math.min(payeDiscretionary * 0.10 / 12, stdPayment);
        plans.add(plan("PAYE",
            payePayment,
            estimateIDRTotal(principal, monthlyRate, payePayment, 240),
            estimateIDRForgiveness(principal, monthlyRate, payePayment, 240),
            20,
            "10% of discretionary income, capped at the standard payment amount. 20-year forgiveness.",
            "new borrowers (after Oct 2007) with high debt and lower income"));

        // IBR New — 10% of discretionary, after July 2014
        double ibrNewPayment = Math.min(payeDiscretionary * 0.10 / 12, stdPayment);
        plans.add(plan("IBR (New Borrowers)",
            ibrNewPayment,
            estimateIDRTotal(principal, monthlyRate, ibrNewPayment, 240),
            estimateIDRForgiveness(principal, monthlyRate, ibrNewPayment, 240),
            20,
            "10% of discretionary income for borrowers who took out loans after July 2014.",
            "borrowers who first borrowed after July 1, 2014 with moderate income"));

        // IBR Old — 15% of discretionary
        double ibrOldPayment = Math.min(payeDiscretionary * 0.15 / 12, stdPayment);
        plans.add(plan("IBR (Prior Borrowers)",
            ibrOldPayment,
            estimateIDRTotal(principal, monthlyRate, ibrOldPayment, 300),
            estimateIDRForgiveness(principal, monthlyRate, ibrOldPayment, 300),
            25,
            "15% of discretionary income. For borrowers with loans before July 2014.",
            "borrowers with loans before July 2014 who do not qualify for PAYE"));

        // ICR — 20% of discretionary above 100% poverty line, or 12-year fixed, whichever is less
        double icrDiscretionary = Math.max(0, agi - povertyLine);
        double icrIDR = icrDiscretionary * 0.20 / 12;
        double icr12yr = amortizedPayment(principal, monthlyRate, 144);
        double icrPayment = Math.min(icrIDR, icr12yr);
        plans.add(plan("ICR",
            icrPayment,
            estimateIDRTotal(principal, monthlyRate, icrPayment, 300),
            estimateIDRForgiveness(principal, monthlyRate, icrPayment, 300),
            25,
            "20% of discretionary income or a 12-year fixed payment, whichever is less.",
            "Parent PLUS borrowers (only IDR option) or those not qualifying for other plans"));

        // Sort by total paid ascending
        plans.sort(Comparator.comparingLong(p -> (Long) p.get("totalPaid")));

        return plans;
    }

    private Map<String, Object> plan(String name, double monthlyPayment, double totalPaid,
                                      double forgiveness, int years, String description, String bestFor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planName",       name);
        m.put("monthlyPayment", Math.round(monthlyPayment));
        m.put("totalPaid",      Math.round(totalPaid));
        m.put("forgiveness",    Math.round(forgiveness));
        m.put("years",          years);
        m.put("description",    description);
        m.put("bestFor",        bestFor);
        return m;
    }

    private double amortizedPayment(double p, double r, int n) {
        if (r == 0) return p / n;
        return p * (r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
    }

    private double gradPaymentTotal(double p, double r) {
        // Approximate: weighted average ~1.3x standard payment
        double std = amortizedPayment(p, r, 120);
        return std * 1.30 * 120;
    }

    private double estimateIDRTotal(double principal, double monthlyRate, double payment, int months) {
        double balance = principal;
        double totalPaid = 0;
        for (int i = 0; i < months && balance > 0; i++) {
            double interest = balance * monthlyRate;
            double actualPayment = Math.min(payment, balance + interest);
            totalPaid += actualPayment;
            balance = balance + interest - actualPayment;
        }
        return totalPaid;
    }

    private double estimateIDRForgiveness(double principal, double monthlyRate, double payment, int months) {
        double balance = principal;
        for (int i = 0; i < months; i++) {
            double interest = balance * monthlyRate;
            balance = balance + interest - payment;
            if (balance <= 0) return 0;
        }
        return Math.max(0, balance);
    }

    // ── PSLF Employer Check ───────────────────────────────────────────────────

    public Map<String, Object> checkPslfEligibility(String employerName) {
        if (employerName == null || employerName.isBlank()) {
            return pslfResult("unknown", false, false,
                "No employer name provided. Please enter your employer to check PSLF eligibility.");
        }

        String lower = employerName.toLowerCase();

        // Check government keywords first
        for (String kw : GOV_KEYWORDS) {
            if (lower.contains(kw)) {
                return pslfResult("government", true, true,
                    "Your employer appears to be a government entity, which automatically qualifies for PSLF. " +
                    "Confirm by submitting an Employment Certification Form (ECF) to your servicer.");
            }
        }

        // Check IRS EO Select (501c3 search)
        try {
            String url = "https://efts.irs.gov/LATEST/search-index?q=" +
                         java.net.URLEncoder.encode(employerName, "UTF-8") + "&limit=5";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("hits")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> hits = (Map<String, Object>) response.get("hits");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
                if (hitList != null && !hitList.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> source = (Map<String, Object>) hitList.get(0).get("_source");
                    if (source != null) {
                        String subSection = String.valueOf(source.getOrDefault("sub_section", ""));
                        boolean is501c3 = "03".equals(subSection) || "3".equals(subSection);
                        if (is501c3) {
                            String orgName = String.valueOf(source.getOrDefault("organization_name", employerName));
                            return pslfResult("nonprofit", true, true,
                                orgName + " is confirmed as a 501(c)(3) nonprofit — your employer qualifies for PSLF. " +
                                "Submit an Employment Certification Form annually to track your payments.");
                        } else {
                            return pslfResult("nonprofit-not-501c3", false, false,
                                "A matching organization was found but it does not appear to be a 501(c)(3) nonprofit. " +
                                "Verify at studentaid.gov or consult your HR department.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            // IRS API unavailable — return manual check guidance
        }

        return pslfResult("unverified", null, false,
            "We could not automatically verify your employer's PSLF status. " +
            "Check using the PSLF Help Tool at studentaid.gov/pslf, or contact your HR department " +
            "to confirm whether your employer is a 501(c)(3) nonprofit or government entity.");
    }

    private Map<String, Object> pslfResult(String type, Boolean eligible, boolean confirmed, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employerType", type);
        m.put("pslfEligible", eligible);
        m.put("confirmed",    confirmed);
        m.put("message",      message);
        return m;
    }
}
