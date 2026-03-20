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
            3. Strategies to minimize total debt accumulation over all 4 years of college, including how borrowing choices each year compound into the full repayment burden at graduation
            4. One actionable tip for this specific situation
            5. Suggest 2-3 specific part-time employment opportunities directly related to %s that a student could realistically do while in school to offset costs (be specific to this field, not generic suggestions)

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
            req.getSixYrEarnings() != null ? String.format("- Median earnings 6 years after graduation: $%.2f/yr", req.getSixYrEarnings()) : "",
            req.getMajor() != null ? req.getMajor() : "the student's chosen major"
        );
    }
}
