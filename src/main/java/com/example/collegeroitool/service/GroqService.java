package com.example.collegeroitool.service;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GroqService {

    // =========================================================================
    //  PROMPT TEMPLATE
    //  Format specifiers in order:
    //  STUDENT DATA  (9 args):
    //    1  %s      college name
    //    2  %s      major
    //    3  %,.0f   annual net price
    //    4  %,.0f   annual federal loans
    //    5  %,.0f   annual unmet need
    //    6  %,.0f   annual free aid
    //    7  %s      median earnings – this major (6 yrs)
    //    8  %s      median earnings – college-wide (6 yrs)
    //    9  %s      student profile
    //  REPAYMENT SCENARIOS  (8 args):
    //   10  %,d     scenario 1 – 4-yr total
    //   11  %,d     scenario 1 – monthly payment
    //   12  %,d     scenario 1 – annual payment
    //   13  %s      scenario 1 – pct of earnings
    //   14  %,d     scenario 2 – 4-yr total
    //   15  %,d     scenario 2 – monthly payment
    //   16  %,d     scenario 2 – annual payment
    //   17  %s      scenario 2 – pct of earnings
    //  FOOTER NOTE  (3 args):
    //   18  %,.0f   unmet need / yr
    //   19  %,.0f   unmet need x 4
    //   20  %,d     scenario 1 total (federal principal)
    // =========================================================================
    private static final String PROMPT_TEMPLATE =
"You are producing an AI Financial Summary for a student.\n" +
"Output ONLY valid HTML — no markdown, no plain text, no code fences.\n" +
"Use inline CSS only. Do not use <html>, <head>, <body>, or <style> tags.\n" +
"\n" +
"STUDENT DATA (use only these pre-calculated numbers — do not recalculate):\n" +
"College: %s\n" +
"Major: %s\n" +
"Annual Net Price: $%,.0f\n" +
"Annual Federal Loans Offered: $%,.0f\n" +
"Annual Unmet Need (after federal loans): $%,.0f\n" +
"Annual Free Aid (Pell + grants + scholarships): $%,.0f\n" +
"Median Earnings - This Major (6 yrs): %s\n" +
"Median Earnings - College-Wide (6 yrs): %s\n" +
"Student profile: %s\n" +
"\n" +
"REPAYMENT SCENARIOS (pre-calculated -- use these exact figures):\n" +
"Scenario 1 (Federal Loans Only): 4-yr=$%,d, monthly=~$%,d, annual=~$%,d, ~%s of 6-yr earnings\n" +
"Scenario 2 (Maximum Borrowing):  4-yr=$%,d, monthly=~$%,d, annual=~$%,d, ~%s of 6-yr earnings\n" +
"\n" +
"===============================================\n" +
"OUTPUT — produce exactly this HTML document structure:\n" +
"===============================================\n" +
"\n" +
"Wrap everything in:\n" +
"<div style=\"font-family:'Segoe UI',Arial,sans-serif;max-width:680px;color:#1a1a1a;line-height:1.6;font-size:14px;\">\n" +
"\n" +
"---\n" +
"SECTION 1: FINANCIAL SUMMARY\n" +
"---\n" +
"Section heading style (reuse for ALL headings):\n" +
"<h3 style=\"color:#1e5c1e;font-size:15px;font-weight:700;margin:20px 0 8px;\">Financial Summary</h3>\n" +
"\n" +
"Intro line: \"Here is a snapshot of your financial picture for this school based on the information available.\"\n" +
"\n" +
"Render a 2-column bordered table:\n" +
"<table style=\"width:100%%;border-collapse:collapse;margin-bottom:10px;\">\n" +
"  <tr>\n" +
"    <td style=\"padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;width:65%%;\">Net Price</td>\n" +
"    <td style=\"padding:8px 12px;text-align:right;border:1px solid #ddd;\">$[net price value]</td>\n" +
"  </tr>\n" +
"  <tr>\n" +
"    <td style=\"padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;\">Federal Loans Offered</td>\n" +
"    <td style=\"padding:8px 12px;text-align:right;border:1px solid #ddd;\">$[federal loans value]</td>\n" +
"  </tr>\n" +
"  <tr>\n" +
"    <td style=\"padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;\">Unmet Need (after loans)</td>\n" +
"    <td style=\"padding:8px 12px;text-align:right;border:1px solid #ddd;\">$[unmet need value]</td>\n" +
"  </tr>\n" +
"</table>\n" +
"\n" +
"Italic note below table:\n" +
"<p style=\"font-size:12px;color:#555;font-style:italic;margin:6px 0 16px;\">Note: Unmet need represents the remaining gap after federal loans are applied. This gap may be covered through additional borrowing, outside scholarships, family contributions, or employment. This tool does not predict how that gap will be filled.</p>\n" +
"<hr style=\"border:none;border-top:1px solid #ddd;margin:16px 0;\">\n" +
"\n" +
"---\n" +
"SECTION 2: EARNINGS DATA\n" +
"---\n" +
"Heading: \"For Context: Earnings Data\"\n" +
"\n" +
"Render a 2-column bordered table:\n" +
"<table style=\"width:100%%;border-collapse:collapse;margin-bottom:10px;\">\n" +
"  <tr>\n" +
"    <td style=\"padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;width:65%%;\">Median Earnings - This Major (6 yrs)</td>\n" +
"    <td style=\"padding:8px 12px;text-align:right;border:1px solid #ddd;\">[this major earnings value]</td>\n" +
"  </tr>\n" +
"  <tr>\n" +
"    <td style=\"padding:8px 12px;font-weight:700;background:#f5f5f5;border:1px solid #ddd;\">Median Earnings - College-Wide (6 yrs)</td>\n" +
"    <td style=\"padding:8px 12px;text-align:right;border:1px solid #ddd;\">[college-wide earnings value]</td>\n" +
"  </tr>\n" +
"</table>\n" +
"CRITICAL: Use ONLY the exact earnings figures from the student data above — do not invent or change these numbers.\n" +
"\n" +
"Italic note:\n" +
"<p style=\"font-size:12px;color:#555;font-style:italic;margin:6px 0 16px;\">Note: These figures reflect median earnings approximately 2 years after completing a 4-year degree. They represent median outcomes and do not guarantee individual results. For majors where graduate school is common, early career earnings may be lower.</p>\n" +
"<hr style=\"border:none;border-top:1px solid #ddd;margin:16px 0;\">\n" +
"\n" +
"---\n" +
"SECTION 3: REPAYMENT SCENARIOS\n" +
"---\n" +
"Heading: \"For Context: Estimated Repayment Scenarios (10-Year Standard Plan)\"\n" +
"\n" +
"Render this 3-column table:\n" +
"<table style=\"width:100%%;border-collapse:collapse;margin-bottom:10px;\">\n" +
"  <thead>\n" +
"    <tr style=\"background:#1e5c1e;color:white;\">\n" +
"      <th style=\"padding:10px 12px;text-align:left;border:1px solid #1e5c1e;font-weight:600;\"> </th>\n" +
"      <th style=\"padding:10px 12px;text-align:center;border:1px solid #1e5c1e;font-weight:700;\">Scenario 1<br><span style=\"font-weight:400;font-size:11px;\">Federal Loans Only</span></th>\n" +
"      <th style=\"padding:10px 12px;text-align:center;border:1px solid #1e5c1e;font-weight:700;\">Scenario 2<br><span style=\"font-weight:400;font-size:11px;\">Maximum Borrowing</span></th>\n" +
"    </tr>\n" +
"  </thead>\n" +
"  <tbody>\n" +
"    <tr>\n" +
"      <td style=\"padding:9px 12px;font-weight:700;border:1px solid #ddd;\">Estimated 4-Year Borrowing</td>\n" +
"      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">$[S1 4yr]</td>\n" +
"      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">$[S2 4yr]</td>\n" +
"    </tr>\n" +
"    <tr style=\"background:#f9f9f9;\">\n" +
"      <td style=\"padding:9px 12px;font-weight:700;border:1px solid #ddd;\">Estimated Monthly Payment</td>\n" +
"      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">~$[S1 monthly]</td>\n" +
"      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">~$[S2 monthly]</td>\n" +
"    </tr>\n" +
"    <tr>\n" +
"      <td style=\"padding:9px 12px;font-weight:700;border:1px solid #ddd;\">Estimated Annual Repayment</td>\n" +
"      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">~$[S1 annual]</td>\n" +
"      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;\">~$[S2 annual]</td>\n" +
"    </tr>\n" +
"    <tr style=\"background:#f9f9f9;\">\n" +
"      <td style=\"padding:9px 12px;font-weight:700;border:1px solid #ddd;\">As %% of 6-Year Median Earnings</td>\n" +
"      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;color:[S1<=10%%:#1e5c1e else #c53030];font-weight:700;\">~[S1 pct] [S1<=10%%: one green square &#9632; else two red squares &#9632;&#9632;]</td>\n" +
"      <td style=\"padding:9px 12px;text-align:center;border:1px solid #ddd;color:[S2<=10%%:#1e5c1e else #c53030];font-weight:700;\">~[S2 pct] [S2<=10%%: one green square &#9632; else two red squares &#9632;&#9632;]</td>\n" +
"    </tr>\n" +
"  </tbody>\n" +
"</table>\n" +
"\n" +
"For the 'As %% of 6-Year Median Earnings' row:\n" +
"- If pct <= 10%%: use color #1e5c1e (green) and one square &#9632;\n" +
"- If pct > 10%%: use color #c53030 (red) and two squares &#9632;&#9632;\n" +
"\n" +
"Italic note below table:\n" +
"<p style=\"font-size:12px;color:#555;font-style:italic;margin:6px 0 16px;\">Note: Repayment estimates assume a 6.5%% federal loan interest rate. Actual rates may vary. Scenario 2 assumes all unmet need is borrowed across 4 years ($%,.0f x 4 = $%,.0f + $%,d federal).</p>\n" +
"<hr style=\"border:none;border-top:1px solid #ddd;margin:16px 0;\">\n" +
"\n" +
"---\n" +
"SECTION 4: DEBT-TO-INCOME BENCHMARK\n" +
"---\n" +
"Heading: \"For Context: Debt-to-Income Benchmark\"\n" +
"\n" +
"<p style=\"font-size:13px;color:#1a1a1a;margin:0 0 16px;\">Financial industry sources generally cite annual student loan repayment under 10%% of income as a manageable threshold. This figure is provided for reference only.</p>\n" +
"<hr style=\"border:none;border-top:1px solid #ddd;margin:16px 0;\">\n" +
"\n" +
"---\n" +
"SECTION 5: EMPLOYMENT OPPORTUNITIES\n" +
"---\n" +
"Heading: \"Possible Employment Opportunities\"\n" +
"\n" +
"Write 3-4 bullet points specific to the student's major about realistic part-time or campus employment paths.\n" +
"Mention how earning while in school reduces borrowing and builds professional experience.\n" +
"Each distinct employment idea = its own <li>.\n" +
"<ul style=\"margin:4px 0 16px;padding-left:20px;font-size:13px;line-height:1.8;color:#1a1a1a;\">\n" +
"  <li style=\"margin-bottom:5px;\">[bullet 1]</li>\n" +
"  <li style=\"margin-bottom:5px;\">[bullet 2]</li>\n" +
"  <li style=\"margin-bottom:5px;\">[bullet 3]</li>\n" +
"  <li style=\"margin-bottom:5px;\">[bullet 4]</li>\n" +
"</ul>\n" +
"<hr style=\"border:none;border-top:1px solid #ddd;margin:16px 0;\">\n" +
"\n" +
"---\n" +
"SECTION 6: ADDITIONAL RESOURCES\n" +
"---\n" +
"Heading: \"Additional Resources to Explore\"\n" +
"\n" +
"<p style=\"font-size:13px;margin:0 0 6px;\">For scholarship opportunities that may apply to your profile, visit:</p>\n" +
"<p style=\"margin:0 0 16px;\"><a href=\"https://the.ismaili/us/en/resources/scholarships\" target=\"_blank\" style=\"color:#1e5c1e;text-decoration:underline;\">https://the.ismaili/us/en/resources/scholarships</a></p>\n" +
"<hr style=\"border:none;border-top:1px solid #ddd;margin:16px 0;\">\n" +
"\n" +
"---\n" +
"SECTION 7: KEY CONSIDERATIONS\n" +
"---\n" +
"Heading: \"Key Considerations\"\n" +
"\n" +
"Write 2-3 sentences of personalized guidance based on the student's profile, major, unmet need, and repayment percentages.\n" +
"Keep it factual and supportive — reference their specific numbers where relevant.\n" +
"<p style=\"font-size:13px;color:#1a1a1a;margin:0 0 16px;\">[personalized text]</p>\n" +
"\n" +
"---\n" +
"FOOTER\n" +
"---\n" +
"<p style=\"font-size:11px;color:#888;font-style:italic;border-top:1px solid #ddd;padding-top:12px;margin-top:16px;\">This document is for informational purposes only.</p>\n" +
"\n" +
"===============================================\n" +
"STRICT RULES:\n" +
"===============================================\n" +
"1. Output ONLY the HTML wrapper div. No text outside HTML tags, no markdown, no code fences.\n" +
"2. Use ONLY the pre-calculated numbers provided. Do not recalculate or invent values.\n" +
"3. Earnings table MUST show the exact This Major and College-Wide values from student data.\n" +
"4. The scholarship URL must be a real <a href> hyperlink.\n" +
"5. Do NOT include any organization name, branding, or program name anywhere in the output.\n" +
"6. Section headings use <h3 style=\"color:#1e5c1e;font-size:15px;font-weight:700;margin:20px 0 8px;\"> — no background color.\n" +
"7. Employment opportunities MUST use <ul><li> format — never plain sentences.\n";

    // =========================================================================

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
        String prompt = buildPrompt(req);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", 3500);
        body.put("temperature", 0.3);

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

    // -------------------------------------------------------------------------
    //  Prompt builder — fills PROMPT_TEMPLATE with exactly 20 ordered args
    // -------------------------------------------------------------------------
    private String buildPrompt(LlmAdviceRequest req) {

        // -- Null-safe field extraction ----------------------------------------
        double subsidized        = nvl(req.getSubsidizedLoan());
        double unsubsidized      = nvl(req.getUnsubsidizedLoan());
        double pellGrant         = nvl(req.getPellGrant());
        double instGrant         = nvl(req.getInstitutionalGrant());
        double scholarship       = nvl(req.getScholarshipAmount());
        double computedNet       = nvl(req.getComputedNetPrice());
        double unmetNeed         = nvl(req.getComputedUnmetNeed());
        double majorEarnings     = nvl(req.getSixYrEarnings());           // This Major
        double collegeEarnings   = nvl(req.getCollegeWideEarnings());     // College-Wide (dynamic)

        double annualFedLoans    = subsidized + unsubsidized;
        double totalFreeAid      = pellGrant + instGrant + scholarship;

        // -- Repayment calculations (10-yr standard, 6.5%) ---------------------
        double r      = 0.065 / 12.0;
        double factor = r * Math.pow(1 + r, 120) / (Math.pow(1 + r, 120) - 1);

        double s1p = annualFedLoans * 4;                 // Scenario 1 principal
        double s2p = s1p + unmetNeed * 4;               // Scenario 2 principal

        long s1m = Math.round(s1p * factor);             // monthly
        long s2m = Math.round(s2p * factor);
        long s1a = s1m * 12;                             // annual
        long s2a = s2m * 12;

        // Use majorEarnings for pct calc if available, fall back to collegeEarnings
        double earningsForPct = majorEarnings > 0 ? majorEarnings : collegeEarnings;
        String s1Pct = earningsForPct > 0 ? Math.round(s1a * 100.0 / earningsForPct) + "%" : "N/A";
        String s2Pct = earningsForPct > 0 ? Math.round(s2a * 100.0 / earningsForPct) + "%" : "N/A";

        // -- String fields -----------------------------------------------------
        String college = req.getCollegeName() != null && !req.getCollegeName().isBlank()
                ? req.getCollegeName() : "This College";
        String major   = req.getMajor() != null && !req.getMajor().isBlank()
                ? req.getMajor() : "Undeclared";

        String majorEarningsStr   = majorEarnings > 0
                ? String.format(Locale.US, "$%,.0f", majorEarnings)   : "Not available";
        String collegeEarningsStr = collegeEarnings > 0
                ? String.format(Locale.US, "$%,.0f", collegeEarnings) : "Not available";

        // -- Student profile ---------------------------------------------------
        StringBuilder profile = new StringBuilder();
        if (Boolean.TRUE.equals(req.getFirstGeneration()))
            profile.append("First-generation college student. ");
        if (req.getGpa() != null)
            profile.append("GPA: ").append(req.getGpa()).append(". ");
        if (req.getGender() != null && !req.getGender().isBlank())
            profile.append("Gender: ").append(req.getGender()).append(". ");
        if (req.getRace() != null && !req.getRace().isBlank())
            profile.append("Race/Ethnicity: ").append(req.getRace()).append(". ");
        if (req.getExtracurriculars() != null && !req.getExtracurriculars().isBlank())
            profile.append("Extracurriculars: ").append(req.getExtracurriculars()).append(". ");
        if (req.getAcademicAchievements() != null && !req.getAcademicAchievements().isBlank())
            profile.append("Achievements: ").append(req.getAcademicAchievements()).append(". ");
        String profileStr = profile.length() > 0 ? profile.toString().trim() : "Not provided";

        // -- Format & return ---------------------------------------------------
        return String.format(Locale.US, PROMPT_TEMPLATE,
                // -- Student Data (args 1-9) --
                college,
                major,
                computedNet,
                annualFedLoans,
                unmetNeed,
                totalFreeAid,
                majorEarningsStr,
                collegeEarningsStr,
                profileStr,
                // -- Repayment Scenarios (args 10-17) --
                (long) s1p,
                s1m,
                s1a,
                s1Pct,
                (long) s2p,
                s2m,
                s2a,
                s2Pct,
                // -- Footer note math (args 18-20) --
                unmetNeed,
                unmetNeed * 4,
                (long) s1p
        );
    }

    // -- Helper: null-safe double ---------------------------------------------
    private static double nvl(Double v) {
        return v != null ? v : 0.0;
    }
}
