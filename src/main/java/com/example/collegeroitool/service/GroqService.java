package com.example.collegeroitool.service;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

// =========================================================================
//  Every number in this report (net price, loans, unmet need, repayment
//  scenarios, earnings) is already computed here in Java before any LLM
//  call happens — none of that requires a language model. The static notes
//  (Debt-to-Income, Additional Resources, Key Considerations, footer) are
//  fixed text, not generated. The ONLY thing that genuinely needs the LLM
//  is the "Possible Employment Opportunities" bullets, which require real
//  knowledge of job titles tied to a major. So Java builds the whole HTML
//  report directly and makes one small, bounded LLM call just for those
//  bullets — instead of asking the model to regenerate this entire
//  multi-table document (and its styling) from scratch on every request,
//  which is what was blowing through Groq's TPM cap on compare requests.
// =========================================================================
@Service
public class GroqService {

    private static final String WRAPPER_OPEN =
        "<div style=\"font-family:'Segoe UI',Arial,sans-serif;max-width:680px;color:#1a1a1a;line-height:1.6;font-size:14px;\">\n";
    private static final String WRAPPER_CLOSE = "</div>";
    private static final String HR = "<hr style=\"border:none;border-top:1px solid #ddd;margin:16px 0;\">\n";

    private static final String DEBT_TO_INCOME_SECTION =
        h3("For Context: Debt-to-Income Benchmark") +
        "<p style=\"font-size:13px;color:#1a1a1a;margin:0 0 16px;\">Financial industry sources generally cite annual student loan repayment under 10% of income as a manageable threshold. This figure is provided for reference only.</p>\n" +
        HR;

    private static final String ADDITIONAL_RESOURCES_SECTION =
        h3("Additional Resources to Explore") +
        "<p style=\"font-size:13px;margin:0 0 6px;\">For scholarship opportunities that may apply to your profile, visit:</p>\n" +
        "<p style=\"margin:0 0 16px;\"><a href=\"https://the.ismaili/us/en/resources/scholarships\" target=\"_blank\" style=\"color:#1e5c1e;text-decoration:underline;\">https://the.ismaili/us/en/resources/scholarships</a></p>\n" +
        HR;

    private static final String KEY_CONSIDERATIONS_SECTION =
        h3("Key Considerations") +
        "<p style=\"font-size:13px;color:#1a1a1a;margin:0 0 16px;\">This information is provided to help you understand your financial aid package. For personalized guidance on managing your educational costs, we encourage you to speak with your school's financial aid office or an independent financial advisor.</p>\n";

    private static final String FOOTER_SECTION =
        HR +
        "<p style=\"font-size:11px;color:#888;font-style:italic;border-top:1px solid #ddd;padding-top:12px;margin-top:16px;\">This document is for informational purposes only.</p>\n";

    private static final String FALLBACK_EMPLOYMENT_BULLETS =
        "  <li style=\"margin-bottom:5px;\">Part-time or work-study positions on campus</li>\n" +
        "  <li style=\"margin-bottom:5px;\">Paid internships or research assistant roles related to your field</li>\n" +
        "  <li style=\"margin-bottom:5px;\">Tutoring or teaching-assistant positions in your department</li>\n" +
        "  <li style=\"margin-bottom:5px;\">Earning while in school reduces the total amount you'll need to borrow</li>\n";

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    // -------------------------------------------------------------------------
    //  Public entry point
    // -------------------------------------------------------------------------
    public String getFinancialAdvice(LlmAdviceRequest req) {
        boolean compareMode = Boolean.TRUE.equals(req.getCompareMode()) && req.getCompareColleges() != null;
        return compareMode ? buildCompareReport(req) : buildSingleReport(req);
    }

    // -------------------------------------------------------------------------
    //  Single-college report — built entirely in Java except employment bullets
    // -------------------------------------------------------------------------
    private String buildSingleReport(LlmAdviceRequest req) {
        double subsidized      = nvl(req.getSubsidizedLoan());
        double unsubsidized    = nvl(req.getUnsubsidizedLoan());
        double computedNet     = nvl(req.getComputedNetPrice());
        double unmetNeed       = nvl(req.getComputedUnmetNeed());
        double majorEarnings   = nvl(req.getSixYrEarnings());
        double collegeEarnings = nvl(req.getCollegeWideEarnings());

        double annualFedLoans = subsidized + unsubsidized;

        double r      = 0.065 / 12.0;
        double factor = r * Math.pow(1 + r, 120) / (Math.pow(1 + r, 120) - 1);

        double s1pRaw = annualFedLoans * 4;
        double s2pRaw = s1pRaw + unmetNeed * 4;
        long s1p = (long) s1pRaw;
        long s2p = (long) s2pRaw;
        long s1m = Math.round(s1pRaw * factor);
        long s2m = Math.round(s2pRaw * factor);
        long s1a = s1m * 12;
        long s2a = s2m * 12;

        double earningsForPct = majorEarnings > 0 ? majorEarnings : collegeEarnings;
        String s1Pct = earningsForPct > 0 ? Math.round(s1a * 100.0 / earningsForPct) + "%" : "N/A";
        String s2Pct = earningsForPct > 0 ? Math.round(s2a * 100.0 / earningsForPct) + "%" : "N/A";
        boolean s1Ok = earningsForPct > 0 && (s1a * 100.0 / earningsForPct) <= 10.0;
        boolean s2Ok = earningsForPct > 0 && (s2a * 100.0 / earningsForPct) <= 10.0;

        String major = req.getMajor() != null && !req.getMajor().isBlank() ? req.getMajor() : "Undeclared";
        String majorEarningsStr   = majorEarnings > 0   ? money(majorEarnings)   : "Not available";
        String collegeEarningsStr = collegeEarnings > 0 ? money(collegeEarnings) : "Not available";

        String employmentHtml = generateEmploymentBulletsSafe(List.of(major));

        return WRAPPER_OPEN
            + singleFinancialSummarySection(computedNet, annualFedLoans, unmetNeed)
            + singleEarningsSection(majorEarningsStr, collegeEarningsStr)
            + h3("For Context: Estimated Repayment Scenarios (10-Year Standard Plan)")
            + repaymentTable(s1p, s1m, s1a, s1Pct, s1Ok, s2p, s2m, s2a, s2Pct, s2Ok)
            + "<p style=\"font-size:12px;color:#555;font-style:italic;margin:6px 0 16px;\">Note: Repayment estimates assume a 6.5% federal loan interest rate. Actual rates may vary. Scenario 2 assumes all unmet need is borrowed across 4 years (" + money(unmetNeed) + " x 4 = " + money(unmetNeed * 4) + " + " + money(s1p) + " federal).</p>\n"
            + HR
            + DEBT_TO_INCOME_SECTION
            + employmentSection(employmentHtml)
            + ADDITIONAL_RESOURCES_SECTION
            + KEY_CONSIDERATIONS_SECTION
            + FOOTER_SECTION
            + WRAPPER_CLOSE;
    }

    private static String singleFinancialSummarySection(double netPrice, double fedLoans, double unmetNeed) {
        return h3("Financial Summary")
            + "<p>Here is a snapshot of your financial picture for this school based on the information available.</p>\n"
            + "<table style=\"width:100%;border-collapse:collapse;margin-bottom:10px;\">\n"
            + singleRow("Net Price", true, netPrice)
            + singleRow("Federal Loans Offered", false, fedLoans)
            + singleRow("Unmet Need (after loans)", false, unmetNeed)
            + "</table>\n"
            + "<p style=\"font-size:12px;color:#555;font-style:italic;margin:6px 0 16px;\">Note: Unmet need represents the remaining gap after federal loans are applied. This gap may be covered through additional borrowing, outside scholarships, family contributions, or employment. This tool does not predict how that gap will be filled.</p>\n"
            + HR;
    }

    private static String singleRow(String label, boolean firstRow, double value) {
        String labelStyle = firstRow
            ? "padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;width:65%;"
            : "padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;";
        return "  <tr>\n    <td style=\"" + labelStyle + "\">" + label + "</td>\n    <td style=\"padding:8px 12px;text-align:right;border:1px solid #ddd;\">" + money(value) + "</td>\n  </tr>\n";
    }

    private static String singleEarningsSection(String majorEarningsStr, String collegeEarningsStr) {
        return h3("For Context: Earnings Data")
            + "<table style=\"width:100%;border-collapse:collapse;margin-bottom:10px;\">\n"
            + "  <tr>\n    <td style=\"padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;width:65%;\">Median Earnings - This Major (6 yrs)</td>\n    <td style=\"padding:8px 12px;text-align:right;border:1px solid #ddd;\">" + majorEarningsStr + "</td>\n  </tr>\n"
            + "  <tr>\n    <td style=\"padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;\">Median Earnings - College-Wide (6 yrs)</td>\n    <td style=\"padding:8px 12px;text-align:right;border:1px solid #ddd;\">" + collegeEarningsStr + "</td>\n  </tr>\n"
            + "</table>\n"
            + "<p style=\"font-size:12px;color:#555;font-style:italic;margin:6px 0 16px;\">Note: These figures reflect median earnings approximately 2 years after completing a 4-year degree. They represent median outcomes and do not guarantee individual results. For majors where graduate school is common, early career earnings may be lower.</p>\n"
            + HR;
    }

    // -------------------------------------------------------------------------
    //  Compare report — built entirely in Java except employment bullets
    // -------------------------------------------------------------------------
    private static final class CollegeCalc {
        String name;
        double coa, netP, unmet, freeAid, fedL, earn;
        long s1p, s1m, s1a, s2p, s2m, s2a;
        String s1Pct, s2Pct;
        boolean s1Ok, s2Ok;
    }

    private String buildCompareReport(LlmAdviceRequest req) {
        List<Map<String, Object>> colleges = req.getCompareColleges();

        double r      = 0.065 / 12.0;
        double factor = r * Math.pow(1 + r, 120) / (Math.pow(1 + r, 120) - 1);

        List<String> selectedMajors = new ArrayList<>();
        for (Map<String, Object> c : colleges) {
            Object mt = c.get("selectedMajorTitle");
            if (mt != null && !String.valueOf(mt).isBlank() && !String.valueOf(mt).equalsIgnoreCase("null")) {
                String title = String.valueOf(mt).trim();
                if (!selectedMajors.contains(title)) selectedMajors.add(title);
            }
        }

        List<CollegeCalc> calcs = new ArrayList<>();
        for (int i = 0; i < colleges.size(); i++) {
            Map<String, Object> c = colleges.get(i);
            CollegeCalc cc = new CollegeCalc();
            cc.name  = String.valueOf(c.getOrDefault("collegeName", "College " + (i + 1)));
            cc.coa   = toDouble(c.get("coa"));
            cc.netP  = toDouble(c.get("netPrice"));
            cc.unmet = toDouble(c.get("unmetNeed"));
            double pell  = toDouble(c.get("pellGrant"));
            double instG = toDouble(c.get("institutionalGrant"));
            double schol = toDouble(c.get("scholarshipAmount"));
            double subL   = toDouble(c.get("subsidizedLoan"));
            double unsubL = toDouble(c.get("unsubsidizedLoan"));
            cc.fedL = subL + unsubL;
            cc.earn = toDouble(c.get("sixYrEarnings"));
            cc.freeAid = pell + instG + schol;

            double s1pRaw = cc.fedL * 4;
            double s2pRaw = s1pRaw + cc.unmet * 4;
            cc.s1p = (long) s1pRaw;
            cc.s2p = (long) s2pRaw;
            cc.s1m = Math.round(s1pRaw * factor);
            cc.s2m = Math.round(s2pRaw * factor);
            cc.s1a = cc.s1m * 12;
            cc.s2a = cc.s2m * 12;

            double earningsForPct = cc.earn > 0 ? cc.earn : 0;
            cc.s1Pct = earningsForPct > 0 ? Math.round(cc.s1a * 100.0 / earningsForPct) + "%" : "N/A";
            cc.s2Pct = earningsForPct > 0 ? Math.round(cc.s2a * 100.0 / earningsForPct) + "%" : "N/A";
            cc.s1Ok = earningsForPct > 0 && (cc.s1a * 100.0 / earningsForPct) <= 10.0;
            cc.s2Ok = earningsForPct > 0 && (cc.s2a * 100.0 / earningsForPct) <= 10.0;

            calcs.add(cc);
        }

        String employmentHtml = generateEmploymentBulletsSafe(selectedMajors);

        StringBuilder perCollegeSections = new StringBuilder();
        for (CollegeCalc cc : calcs) {
            perCollegeSections.append(comparePerCollegeBlock(cc));
        }

        return WRAPPER_OPEN
            + compareOverviewSection(calcs)
            + perCollegeSections
            + compareEarningsSection(calcs)
            + DEBT_TO_INCOME_SECTION
            + employmentSection(employmentHtml)
            + ADDITIONAL_RESOURCES_SECTION
            + KEY_CONSIDERATIONS_SECTION
            + FOOTER_SECTION
            + WRAPPER_CLOSE;
    }

    private static String compareOverviewSection(List<CollegeCalc> calcs) {
        double minUnmet = calcs.stream().mapToDouble(c -> c.unmet).min().orElse(0);
        double maxUnmet = calcs.stream().mapToDouble(c -> c.unmet).max().orElse(0);
        boolean tie = minUnmet == maxUnmet;

        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < calcs.size(); i++) {
            CollegeCalc cc = calcs.get(i);
            String rowBg = (i % 2 == 0) ? "#fff" : "#f9f9f9";
            String unmetStyle;
            if (!tie && cc.unmet == minUnmet) {
                unmetStyle = "padding:9px 12px;text-align:right;border:1px solid #ddd;background:#f0fdf4;color:#276749;font-weight:700;";
            } else if (!tie && cc.unmet == maxUnmet) {
                unmetStyle = "padding:9px 12px;text-align:right;border:1px solid #ddd;background:#fff5f5;color:#c53030;font-weight:700;";
            } else {
                unmetStyle = "padding:9px 12px;text-align:right;border:1px solid #ddd;";
            }
            rows.append("  <tr style=\"background:").append(rowBg).append(";\">\n")
                .append("    <td style=\"padding:9px 12px;text-align:left;border:1px solid #ddd;\">").append(escape(cc.name)).append("</td>\n")
                .append("    <td style=\"padding:9px 12px;text-align:right;border:1px solid #ddd;\">").append(money(cc.coa)).append("</td>\n")
                .append("    <td style=\"padding:9px 12px;text-align:right;border:1px solid #ddd;\">").append(money(cc.netP)).append("</td>\n")
                .append("    <td style=\"").append(unmetStyle).append("\">").append(money(cc.unmet)).append("</td>\n")
                .append("    <td style=\"padding:9px 12px;text-align:right;border:1px solid #ddd;\">").append(money(cc.freeAid)).append("</td>\n")
                .append("  </tr>\n");
        }

        return h3("Comparison at a Glance")
            + "<p style=\"font-size:13px;margin:0 0 12px;\">Here is a side-by-side breakdown of annual cost and coverage for each school based on the information provided.</p>\n"
            + "<table style=\"width:100%;border-collapse:collapse;margin-bottom:10px;font-size:13px;\">\n"
            + "  <thead><tr style=\"background:#1e5c1e;color:white;\">\n"
            + "    <th style=\"padding:9px 12px;text-align:left;border:1px solid #1e5c1e;\">College</th>\n"
            + "    <th style=\"padding:9px 12px;text-align:right;border:1px solid #1e5c1e;\">COA/yr</th>\n"
            + "    <th style=\"padding:9px 12px;text-align:right;border:1px solid #1e5c1e;\">Net Price</th>\n"
            + "    <th style=\"padding:9px 12px;text-align:right;border:1px solid #1e5c1e;\">Unmet Need</th>\n"
            + "    <th style=\"padding:9px 12px;text-align:right;border:1px solid #1e5c1e;\">Free Aid</th>\n"
            + "  </tr></thead>\n"
            + "  <tbody>\n" + rows + "  </tbody>\n"
            + "</table>\n"
            + HR;
    }

    private static String comparePerCollegeBlock(CollegeCalc cc) {
        String name = escape(cc.name);
        return h3(name + " — Financial Summary")
            + "<p style=\"font-size:13px;margin:0 0 8px;\">Here is a snapshot of your financial picture for this school based on the information provided.</p>\n"
            + "<table style=\"width:100%;border-collapse:collapse;margin-bottom:10px;\">\n"
            + compareRow("Net Price", true, cc.netP)
            + compareRow("Federal Loans Offered", false, cc.fedL)
            + compareRow("Unmet Need (after loans)", false, cc.unmet)
            + "</table>\n"
            + "<p style=\"font-size:12px;color:#555;font-style:italic;margin:6px 0 16px;\">Note: Unmet need represents the remaining gap after federal loans are applied. This gap may be covered through additional borrowing, outside scholarships, family contributions, or employment. This tool does not predict how that gap will be filled.</p>\n"
            + h3(name + " — Estimated Repayment Scenarios (10-Year Standard Plan)")
            + repaymentTable(cc.s1p, cc.s1m, cc.s1a, cc.s1Pct, cc.s1Ok, cc.s2p, cc.s2m, cc.s2a, cc.s2Pct, cc.s2Ok)
            + "<p style=\"font-size:12px;color:#555;font-style:italic;margin:6px 0 16px;\">Note: Repayment estimates assume a 6.5% federal loan interest rate. Actual rates may vary. Scenario 2 assumes all unmet need is borrowed across 4 years (" + money(cc.unmet) + " x 4 = " + money(cc.unmet * 4) + " + " + money(cc.s1p) + " federal).</p>\n"
            + HR;
    }

    private static String compareRow(String label, boolean firstRow, double value) {
        String labelStyle = firstRow
            ? "padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;width:65%;"
            : "padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;";
        return "  <tr><td style=\"" + labelStyle + "\">" + label + "</td>\n"
            + "      <td style=\"padding:8px 12px;text-align:right;border:1px solid #ddd;\">" + money(value) + "/yr</td></tr>\n";
    }

    private static String compareEarningsSection(List<CollegeCalc> calcs) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < calcs.size(); i++) {
            CollegeCalc cc = calcs.get(i);
            String rowBg = (i % 2 == 0) ? "#fff" : "#f9f9f9";
            String earnStr = cc.earn > 0 ? money(cc.earn) : "Not available";
            rows.append("  <tr style=\"background:").append(rowBg).append(";\">\n")
                .append("    <td style=\"padding:9px 12px;text-align:left;border:1px solid #ddd;\">").append(escape(cc.name)).append("</td>\n")
                .append("    <td style=\"padding:9px 12px;text-align:right;border:1px solid #ddd;\">").append(earnStr).append("</td>\n")
                .append("  </tr>\n");
        }
        return h3("For Context: Earnings Data")
            + "<table style=\"width:100%;border-collapse:collapse;margin-bottom:10px;\">\n"
            + "  <thead><tr style=\"background:#1e5c1e;color:white;\">\n"
            + "    <th style=\"padding:9px 12px;text-align:left;border:1px solid #1e5c1e;\">College</th>\n"
            + "    <th style=\"padding:9px 12px;text-align:right;border:1px solid #1e5c1e;\">6-yr Median Earnings</th>\n"
            + "  </tr></thead>\n"
            + "  <tbody>\n" + rows + "  </tbody>\n"
            + "</table>\n"
            + "<p style=\"font-size:12px;color:#555;font-style:italic;margin:6px 0 16px;\">Note: These figures reflect median earnings approximately 2 years after completing a 4-year degree. They represent median outcomes and do not guarantee individual results. For majors where graduate school is common, early career earnings may be lower.</p>\n"
            + HR;
    }

    // -------------------------------------------------------------------------
    //  Shared HTML builders
    // -------------------------------------------------------------------------
    private static String h3(String text) {
        return "<h3 style=\"color:#1e5c1e;font-size:15px;font-weight:700;margin:20px 0 8px;\">" + text + "</h3>\n";
    }

    private static String employmentSection(String bulletsHtml) {
        return h3("Possible Employment Opportunities")
            + "<ul style=\"margin:4px 0 16px;padding-left:20px;font-size:13px;line-height:1.8;color:#1a1a1a;\">\n"
            + bulletsHtml
            + "</ul>\n"
            + HR;
    }

    private static String repaymentTable(long s1p, long s1m, long s1a, String s1Pct, boolean s1Ok,
                                          long s2p, long s2m, long s2a, String s2Pct, boolean s2Ok) {
        return "<table style=\"width:100%;border-collapse:collapse;margin-bottom:10px;\">\n"
            + "  <thead>\n"
            + "    <tr style=\"background:#1e5c1e;color:white;\">\n"
            + "      <th style=\"padding:10px 12px;text-align:left;border:1px solid #1e5c1e;font-weight:600;\"> </th>\n"
            + "      <th style=\"padding:10px 12px;text-align:center;border:1px solid #1e5c1e;font-weight:700;\">Scenario 1<br><span style=\"font-weight:400;font-size:11px;\">Federal Loans Only</span></th>\n"
            + "      <th style=\"padding:10px 12px;text-align:center;border:1px solid #1e5c1e;font-weight:700;\">Scenario 2<br><span style=\"font-weight:400;font-size:11px;\">Maximum Borrowing</span></th>\n"
            + "    </tr>\n"
            + "  </thead>\n"
            + "  <tbody>\n"
            + "    <tr>\n"
            + "      <td style=\"padding:9px 12px;font-weight:700;border:1px solid #ddd;\">Estimated 4-Year Borrowing</td>\n"
            + "      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">" + money(s1p) + "</td>\n"
            + "      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">" + money(s2p) + "</td>\n"
            + "    </tr>\n"
            + "    <tr style=\"background:#f9f9f9;\">\n"
            + "      <td style=\"padding:9px 12px;font-weight:700;border:1px solid #ddd;\">Estimated Monthly Payment</td>\n"
            + "      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">~" + money(s1m) + "</td>\n"
            + "      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">~" + money(s2m) + "</td>\n"
            + "    </tr>\n"
            + "    <tr>\n"
            + "      <td style=\"padding:9px 12px;font-weight:700;border:1px solid #ddd;\">Estimated Annual Repayment</td>\n"
            + "      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">~" + money(s1a) + "</td>\n"
            + "      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">~" + money(s2a) + "</td>\n"
            + "    </tr>\n"
            + "    <tr style=\"background:#f9f9f9;\">\n"
            + "      <td style=\"padding:9px 12px;font-weight:700;border:1px solid #ddd;\">As % of 6-Year Median Earnings</td>\n"
            + "      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;color:" + (s1Ok ? "#1e5c1e" : "#c53030") + ";font-weight:700;\">~" + s1Pct + " " + (s1Ok ? "&#9632;" : "&#9632;&#9632;") + "</td>\n"
            + "      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;color:" + (s2Ok ? "#1e5c1e" : "#c53030") + ";font-weight:700;\">~" + s2Pct + " " + (s2Ok ? "&#9632;" : "&#9632;&#9632;") + "</td>\n"
            + "    </tr>\n"
            + "  </tbody>\n"
            + "</table>\n";
    }

    private static String money(double v) {
        return String.format(Locale.US, "$%,.0f", v);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // -------------------------------------------------------------------------
    //  Employment bullets — the only genuinely LLM-generated part of the report.
    //  Bounded by design (at most 5 unique majors, from the frontend's own max-5-
    //  colleges cap), so this call is nowhere close to the TPM ceiling. If it
    //  fails for any reason, fall back to generic bullets rather than failing
    //  the whole report — everything else here is deterministic and shouldn't
    //  depend on the LLM being available.
    // -------------------------------------------------------------------------
    private String generateEmploymentBulletsSafe(List<String> majors) {
        try {
            String bullets = generateEmploymentBullets(majors);
            return (bullets == null || bullets.isBlank()) ? FALLBACK_EMPLOYMENT_BULLETS : bullets;
        } catch (Exception e) {
            return FALLBACK_EMPLOYMENT_BULLETS;
        }
    }

    private String generateEmploymentBullets(List<String> majors) {
        String prompt;
        int maxTokens;

        if (majors.isEmpty()) {
            prompt = "Write exactly 2 HTML bullets (format: <li style=\"margin-bottom:5px;\">...</li>) suggesting "
                + "realistic campus employment opportunities for a college student (no major specified). Each must "
                + "name a real, concrete job title/role — no generic phrases like 'campus jobs' or 'freelance work'. "
                + "The last bullet should note that earning while studying reduces total borrowing. "
                + "Output ONLY the <li> lines — no <ul> wrapper, no markdown, no commentary, no code fences.";
            maxTokens = 220;
        } else if (majors.size() == 1) {
            prompt = "Write exactly 4 HTML bullets (format: <li style=\"margin-bottom:5px;\">...</li>) for the major: "
                + majors.get(0) + ". Each bullet must name a real, concrete job title/role tied to this exact field "
                + "of study — no generic phrases like 'internships related to your major', 'part-time campus jobs', "
                + "or 'freelance work'. Example: Computer Science -> 'Software developer intern at a local startup "
                + "or university IT department'. The last bullet should note that earning while in school reduces "
                + "total borrowing. Output ONLY the <li> lines — no <ul> wrapper, no markdown, no commentary, no "
                + "code fences.";
            maxTokens = 350;
        } else {
            String majorsList = majors.stream().map(m -> "- " + m).collect(Collectors.joining("\n"));
            prompt = "Write exactly one HTML bullet (format: <li style=\"margin-bottom:5px;\">...</li>) per major "
                + "listed below, in order. Each bullet: start with the major name in bold "
                + "(<strong>MajorName:</strong>) followed by one specific, real job title/role tied to that field — "
                + "no generic phrases like 'internships related to your major' or 'campus jobs'. The last bullet "
                + "should note that earning while studying reduces total borrowing.\nMajors:\n" + majorsList
                + "\nOutput ONLY the <li> lines — no <ul> wrapper, no markdown, no commentary, no code fences.";
            maxTokens = 160 * majors.size() + 100;
        }

        return sanitizeBullets(callGroq(prompt, maxTokens));
    }

    private static String sanitizeBullets(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replaceAll("(?s)^```[a-zA-Z]*\\n?", "").replaceAll("```\\s*$", "");
        return s.isBlank() ? "" : s.trim() + "\n";
    }

    private String callGroq(String prompt, int maxTokens) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.3);
        // gpt-oss models spend hidden "reasoning" tokens out of the same TPM budget as the
        // visible output; this is a short, bounded generation task, not one needing deep
        // reasoning, so keep reasoning effort low.
        if (model != null && model.contains("gpt-oss")) {
            body.put("reasoning_effort", "low");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.getBody().get("choices");
        @SuppressWarnings("unchecked")
        Map<String, Object> messageResp =
                (Map<String, Object>) choices.get(0).get("message");
        return (String) messageResp.get("content");
    }

    // -- Helper: null-safe double from Object ---------------------------------
    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }

    // -- Helper: null-safe double ---------------------------------------------
    private static double nvl(Double v) {
        return v != null ? v : 0.0;
    }
}
