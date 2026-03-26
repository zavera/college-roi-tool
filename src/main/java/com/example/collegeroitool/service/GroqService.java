package com.example.collegeroitool.service;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getFinancialAdvice(LlmAdviceRequest req) {
        String prompt = buildPrompt(req);

        // Build request body following OpenAI chat completions format
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", 1100);
        body.put("temperature", 0.7);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        // Parse response: choices[0].message.content
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> messageResp = (Map<String, Object>) firstChoice.get("message");
        return (String) messageResp.get("content");
    }

    private String buildPrompt(LlmAdviceRequest req) {
        double subsidized   = req.getSubsidizedLoan()     != null ? req.getSubsidizedLoan()     : 0;
        double unsubsidized = req.getUnsubsidizedLoan()   != null ? req.getUnsubsidizedLoan()   : 0;
        double familyCont   = req.getParentPlusLoan()     != null ? req.getParentPlusLoan()     : 0;
        double pellGrant    = req.getPellGrant()           != null ? req.getPellGrant()           : 0;
        double instGrant    = req.getInstitutionalGrant()  != null ? req.getInstitutionalGrant() : 0;
        double scholarship  = req.getScholarshipAmount()   != null ? req.getScholarshipAmount()  : 0;
        double workStudy    = req.getWorkStudy()           != null ? req.getWorkStudy()           : 0;
        double annualFedLoans = subsidized + unsubsidized;
        double totalFreeAid   = pellGrant + instGrant + scholarship;
        double computedNet    = req.getComputedNetPrice()  != null ? req.getComputedNetPrice()  : 0;
        double unmetNeed      = req.getComputedUnmetNeed() != null ? req.getComputedUnmetNeed() : 0;
        double sixYrEarnings  = req.getSixYrEarnings()     != null ? req.getSixYrEarnings()     : 0;

        // 10-year standard repayment: P × r(1+r)^120 / ((1+r)^120 − 1), r = 6.5%/12
        double r      = 0.065 / 12.0;
        double factor = r * Math.pow(1 + r, 120) / (Math.pow(1 + r, 120) - 1);
        double s1p    = annualFedLoans * 4;
        double s2p    = s1p + unmetNeed * 4;
        long   s1m    = Math.round(s1p * factor);
        long   s2m    = Math.round(s2p * factor);
        long   s1a    = s1m * 12;
        long   s2a    = s2m * 12;
        String s1Pct  = sixYrEarnings > 0 ? Math.round(s1a * 100.0 / sixYrEarnings) + "%" : "N/A";
        String s2Pct  = sixYrEarnings > 0 ? Math.round(s2a * 100.0 / sixYrEarnings) + "%" : "N/A";

        // Student profile block
        StringBuilder profile = new StringBuilder();
        if (req.getFirstGeneration() != null && req.getFirstGeneration())
            profile.append("First-generation college student. ");
        if (req.getGpa() != null)
            profile.append("GPA: ").append(req.getGpa()).append(". ");
        if (req.getRace() != null && !req.getRace().isBlank())
            profile.append("Race/Ethnicity: ").append(req.getRace()).append(". ");
        if (req.getExtracurriculars() != null && !req.getExtracurriculars().isBlank())
            profile.append("Extracurriculars: ").append(req.getExtracurriculars()).append(". ");
        if (req.getAcademicAchievements() != null && !req.getAcademicAchievements().isBlank())
            profile.append("Achievements: ").append(req.getAcademicAchievements()).append(". ");

        String college  = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major    = req.getMajor()       != null ? req.getMajor()       : "unspecified";

        return String.format("""
You are producing an AI Financial Summary on behalf of the Aga Khan Education Board (AKEB) EFAS Program. Output structured plain text matching the exact format below. Use only the pre-calculated numbers provided — do not recalculate them.

STUDENT DATA:
College: %s
Major: %s
Annual Net Price (Scorecard): $%,.0f
Annual Federal Loans Offered: $%,.0f
Annual Unmet Need (after federal loans): $%,.0f
Annual Free Aid (Pell + grants + scholarships): $%,.0f
Median Earnings 6 yrs: %s
Student profile: %s

REPAYMENT SCENARIOS (pre-calculated — use these exact figures):
Scenario 1 (Federal Loans Only): 4-yr total = $%,d, monthly = ~$%,d, annual = ~$%,d, ~%s of 6-yr earnings
Scenario 2 (Maximum Borrowing):  4-yr total = $%,d, monthly = ~$%,d, annual = ~$%,d, ~%s of 6-yr earnings

OUTPUT — respond using exactly this structure:

Financial Summary
Here is a snapshot of your financial picture for %s based on the information available.

Net Price: $%,.0f
Federal Loans Offered: $%,.0f
Unmet Need (after loans): $%,.0f

Note: Unmet need represents the remaining gap after federal loans are applied. This gap may be covered through additional borrowing, outside scholarships, family contributions, or employment. This tool does not predict how that gap will be filled.

---

For Context: Earnings Data

Median Earnings – This Major (6 yrs): %s
Median Earnings – College-Wide (6 yrs): %s

Note: These figures reflect median earnings approximately 2 years after completing a 4-year degree. They represent median outcomes and do not guarantee individual results. For majors where graduate school is common, early career earnings may be lower.

---

For Context: Estimated Repayment Scenarios (10-Year Standard Plan)

                                  Scenario 1                Scenario 2
                               Federal Loans Only        Maximum Borrowing
Estimated 4-Year Borrowing:      $%,d                      $%,d
Estimated Monthly Payment:       ~$%,d                     ~$%,d
Estimated Annual Repayment:      ~$%,d                     ~$%,d
As %% of 6-Year Median Earnings:  ~%s                       ~%s

Note: Repayment estimates assume a 6.5%% federal loan interest rate. Actual rates may vary. Scenario 2 assumes all unmet need is borrowed across 4 years ($%,.0f x 4 = $%,.0f + $%,.0f federal).

---

For Context: Debt-to-Income Benchmark

Financial industry sources generally cite annual student loan repayment under 10%% of income as a manageable threshold. This figure is provided for reference only.

---

Possible Employment Opportunities

[Write 2-3 sentences on realistic part-time or campus employment paths for this student, specific to their major. Mention how earning while in school reduces borrowing and builds professional experience.]

---

Additional Resources to Explore

For scholarship opportunities that may apply to your profile, visit:
https://the.ismaili/us/en/resources/scholarships

---

Key Considerations

[Write 3-5 concise, actionable key considerations specific to this student's financial picture. Reference the debt-to-income benchmark. Be honest about risks, remain supportive. Plain language for an 18-22 year old.]

---
Aga Khan Education Board USA – EFAS Program | This document is for informational purposes only.
""",
            college, major,
            computedNet, annualFedLoans, unmetNeed, totalFreeAid,
            sixYrEarnings > 0 ? String.format("$%,.0f/yr", sixYrEarnings) : "Not available",
            profile.length() > 0 ? profile.toString() : "Not provided",
            (long) s1p, s1m, s1a, s1Pct,
            (long) s2p, s2m, s2a, s2Pct,
            college,
            computedNet, annualFedLoans, unmetNeed,
            sixYrEarnings > 0 ? String.format("$%,.0f/yr", sixYrEarnings) : "Not available",
            sixYrEarnings > 0 ? String.format("$%,.0f/yr", sixYrEarnings) : "Not available",
            (long) s1p, (long) s2p,
            s1m, s2m,
            s1a, s2a,
            s1Pct, s2Pct,
            unmetNeed, unmetNeed * 4, s1p
        );
    }
}
