package com.example.collegeroitool.service;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.dto.PremiumInsightsRequest;
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

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private static final String DEV_STUB_KEY = "GROQ_KEY_SET_VIA_ENV";

    private final RestTemplate restTemplate = new RestTemplate();

    public String getFinancialAdvice(LlmAdviceRequest req) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return buildDevStubAdvice(req);
        }
        String prompt = buildPrompt(req);

        // Build request body following OpenAI chat completions format
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", 600);
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

    private String buildDevStubAdvice(LlmAdviceRequest req) {
        String college = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        return String.format("""
            1. Loan Assessment
            [DEV MODE] Based on the figures entered for %s, the federal loan amount appears within typical range. In production, this section will analyze whether your loan-to-earnings ratio is sustainable given projected starting salaries in your field.

            2. Key Risks
            [DEV MODE] Key risks would include interest accrual during enrollment, borrowing across all 4 years compounding the total burden, and dependency on Parent PLUS loans which have fewer repayment protections than federal student loans.

            3. Compounding Impact
            [DEV MODE] Borrowing even $1,000 more per year than necessary adds roughly $1,200–$1,500 to your total repayment burden over a standard 10-year plan due to interest. Small annual decisions compound significantly by graduation.

            4. Action Step
            [DEV MODE] Complete your FAFSA early each year and appeal your aid package in writing if your family's financial situation has changed — this one step recovers an average of $2,000–$4,000 in additional grant aid for students who ask.
            """, college);
    }

    private String buildDevStubInsights(PremiumInsightsRequest req) {
        String college = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major = req.getMajor() != null ? req.getMajor() : "your major";
        return String.format("""
            {
              "scholarships": [
                {"title": "[DEV] Merit Excellence Award", "amount": "Up to $8,000/yr", "details": "Available to students at %s with GPA 3.5+. In production this will reflect real institutional scholarships for %s."},
                {"title": "[DEV] Need-Based Supplemental Grant", "amount": "$3,000–$6,000", "details": "Requires updated FAFSA and counselor letter demonstrating unmet need. Appeals can be submitted each semester."},
                {"title": "[DEV] External Profile Match", "amount": "$2,500 est.", "details": "Fastweb and BigFuture matches based on your demographic and academic profile. Production will surface live results."}
              ],
              "employment": [
                {"title": "[DEV] Undergraduate Research Assistant", "pay": "$13–$16/hr", "details": "8–10 hrs/week through the %s department. Apply via internal postings each semester."},
                {"title": "[DEV] Co-op Placement", "pay": "$17–$22/hr", "details": "Alternating semester placement in %s-related firms. Reduces net annual cost by up to $9,000 and converts to full-time at 60%% rate."},
                {"title": "[DEV] Campus Work-Study Role", "pay": "$12–$14/hr", "details": "Federal work-study positions available through the financial aid office. Earnings do not count against next year's aid eligibility."}
              ]
            }
            """, college, major, major, major);
    }

    public String getPremiumInsights(PremiumInsightsRequest req) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return buildDevStubInsights(req);
        }
        String prompt = String.format("""
            You are a college financial aid expert. A student is attending %s%s.
            %s
            %s

            Return ONLY valid JSON — no markdown, no explanation — in exactly this format:
            {
              "scholarships": [
                {"title": "...", "amount": "...", "details": "..."},
                {"title": "...", "amount": "...", "details": "..."},
                {"title": "...", "amount": "...", "details": "..."}
              ],
              "employment": [
                {"title": "...", "pay": "...", "details": "..."},
                {"title": "...", "pay": "...", "details": "..."},
                {"title": "...", "pay": "...", "details": "..."}
              ]
            }

            For scholarships: list 3 realistic awards relevant to this college, major, and financial profile.
            For employment: list 3 realistic on-campus or field-aligned part-time roles that reduce net cost.
            Keep each details field to 1-2 sentences. Be specific to the institution and major.
            """,
            req.getCollegeName() != null ? req.getCollegeName() : "this college",
            req.getMajor() != null ? ", pursuing " + req.getMajor() : "",
            req.getNetPrice() != null ? String.format("Net price: $%.0f/yr.", req.getNetPrice()) : "",
            req.getSixYrEarnings() != null ? String.format("Median earnings 6 years after graduation: $%.0f/yr.", req.getSixYrEarnings()) : ""
        );

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", 800);
        body.put("temperature", 0.4);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            apiUrl, new HttpEntity<>(body, headers), Map.class);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        return (String) msg.get("content");
    }

    private String buildPrompt(LlmAdviceRequest req) {
        double totalLoans = (req.getFederalLoan() != null ? req.getFederalLoan() : 0)
                          + (req.getParentPlusLoan() != null ? req.getParentPlusLoan() : 0);
        double totalFreeAid = (req.getGrantAmount() != null ? req.getGrantAmount() : 0)
                            + (req.getScholarshipAmount() != null ? req.getScholarshipAmount() : 0);

        return String.format("""
            You are a financial literacy advisor helping undergraduate students and education counselors make responsible decisions about student loans.

            A student is considering the following for ONE academic year at %s%s:
            - Federal Student Loan: $%.2f
            - Parent PLUS Loan: $%.2f
            - Grants (free money): $%.2f
            - Scholarships/Gift Aid: $%.2f
            - Total loans this year: $%.2f
            - Total free aid this year: $%.2f
            %s
            %s

            Please provide clear, practical guidance on:
            1. Whether this loan amount is reasonable given the projected earnings
            2. Key risks to be aware of
            3. How borrowing choices each year compound into the full repayment burden at graduation
            4. One actionable tip to reduce loan dependency for this specific situation

            Keep the response concise, supportive, and easy to understand for an 18-22 year old.
            """,
            req.getCollegeName() != null ? req.getCollegeName() : "this college",
            req.getMajor() != null ? ", pursuing " + req.getMajor() : "",
            req.getFederalLoan() != null ? req.getFederalLoan() : 0,
            req.getParentPlusLoan() != null ? req.getParentPlusLoan() : 0,
            req.getGrantAmount() != null ? req.getGrantAmount() : 0,
            req.getScholarshipAmount() != null ? req.getScholarshipAmount() : 0,
            totalLoans,
            totalFreeAid,
            req.getNetPrice() != null ? String.format("- College Net Price: $%.2f/yr", req.getNetPrice()) : "",
            req.getSixYrEarnings() != null ? String.format("- Median earnings 6 years after graduation: $%.2f/yr", req.getSixYrEarnings()) : ""
        );
    }
}
