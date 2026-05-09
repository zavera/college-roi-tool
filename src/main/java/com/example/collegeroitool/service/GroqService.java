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
                {"title": "[DEV] Merit Excellence Award", "amount": "Up to $8,000/yr", "details": "Available to students at %s with GPA 3.5+. In production this will reflect real institutional scholarships for %s.", "url": "https://www.fastweb.com/"},
                {"title": "[DEV] Need-Based Supplemental Grant", "amount": "$3,000–$6,000", "details": "Requires updated FAFSA and counselor letter demonstrating unmet need. Appeals can be submitted each semester.", "url": "https://bigfuture.collegeboard.org/scholarship-search"},
                {"title": "[DEV] External Profile Match", "amount": "$2,500 est.", "details": "Fastweb and BigFuture matches based on your demographic and academic profile. Production will surface live results.", "url": "https://www.scholarships.com/"}
              ],
              "employment": [
                {"title": "[DEV] Undergraduate Research Assistant — %s Dept.", "pay": "Volunteer for Credit / $13–$15/hr", "type": "Undergraduate Research", "details": "Faculty in the %s program are actively recruiting research assistants each semester. Contact the department office or check the faculty directory for open positions. Volunteer-for-credit positions count toward graduation requirements.", "url": "https://app.joinhandshake.com/"},
                {"title": "[DEV] Department Teaching Assistant / Tutor", "pay": "$12–$14/hr", "type": "On-Campus Employment", "details": "On-campus tutoring and TA roles for %s students are posted each term through the department. Federal work-study eligible — earnings do not affect next year's aid calculation.", "url": "https://app.joinhandshake.com/"},
                {"title": "[DEV] Co-op / Practicum Placement", "pay": "$17–$22/hr", "type": "Co-op / Internship", "details": "Alternating-semester co-op in %s-related firms near campus. Reduces net annual cost by up to $9,000 and converts to full-time offers at a 60%% rate. Apply through the career center in Year 2.", "url": "https://app.joinhandshake.com/"}
              ],
              "yearlyTips": [
                "File FAFSA in October and submit a written aid appeal. First-year borrowers set the baseline — keep federal loans at or below $5,500.",
                "Request a merit aid review if your GPA improved. Explore department research stipends to offset Year 2 cost increases.",
                "Apply for upperclassman scholarships (most have fewer applicants). Consider co-op or internship income to reduce Parent PLUS dependency.",
                "Maximize work-study and check if your employer offers tuition assistance. A strong senior year GPA opens post-grad loan forgiveness programs."
              ]
            }
            """, college, major, major, major, major, major);
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
                {"title": "...", "amount": "...", "details": "...", "url": "https://..."},
                {"title": "...", "amount": "...", "details": "...", "url": "https://..."},
                {"title": "...", "amount": "...", "details": "...", "url": "https://..."}
              ],
              "employment": [
                {"title": "...", "pay": "...", "details": "...", "url": "https://..."},
                {"title": "...", "pay": "...", "details": "...", "url": "https://..."},
                {"title": "...", "pay": "...", "details": "...", "url": "https://..."}
              ],
              "yearlyTips": [
                "Year 1 cost reduction strategy (1 sentence)",
                "Year 2 cost reduction strategy (1 sentence)",
                "Year 3 cost reduction strategy (1 sentence)",
                "Year 4 cost reduction strategy (1 sentence)"
              ]
            }

            For scholarships: list 3 realistic awards relevant to this college, major, and financial profile. Include a real URL to apply or learn more (scholarship database, college financial aid page, fastweb.com, or bigfuture.collegeboard.org).
            For employment: list exactly 3 CAMPUS-BASED, MAJOR-SPECIFIC opportunities in this order:
              1. Undergraduate Research opportunity — specify if faculty in this major are actively hiring (research assistant, lab work). Include "Volunteer for Credit" as type if unpaid. Include a faculty contact name or department if realistic.
              2. A major-specific on-campus paid role (department assistant, tutoring, lab tech, studio monitor, etc.).
              3. A co-op, internship, or practicum tied to this major near this campus.
            Add a "type" field: "Undergraduate Research", "On-Campus Employment", or "Co-op / Internship".
            Include a real URL for each (Handshake, college career center, department page, or O*NET).
            For yearlyTips: one actionable cost-reduction strategy per academic year.
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
