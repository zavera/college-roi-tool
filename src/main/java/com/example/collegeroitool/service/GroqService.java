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

    /** Generic single-prompt completion — used by the RoadMap and any ad-hoc front-end call. */
    public String getCompletion(String prompt) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", 1400);
        body.put("temperature", 0.45);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> messageResp = (Map<String, Object>) firstChoice.get("message");
        return (String) messageResp.get("content");
    }

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
        double subsidized   = req.getSubsidizedLoan()   != null ? req.getSubsidizedLoan()   : 0;
        double unsubsidized = req.getUnsubsidizedLoan() != null ? req.getUnsubsidizedLoan() : 0;
        double parentPlus   = req.getParentPlusLoan()   != null ? req.getParentPlusLoan()   : 0;
        double pellGrant    = req.getPellGrant()         != null ? req.getPellGrant()         : 0;
        double instGrant    = req.getInstitutionalGrant()!= null ? req.getInstitutionalGrant(): 0;
        double scholarship  = req.getScholarshipAmount() != null ? req.getScholarshipAmount(): 0;
        double workStudy    = req.getWorkStudy()         != null ? req.getWorkStudy()         : 0;
        double totalLoans   = subsidized + unsubsidized + parentPlus;
        double totalFreeAid = pellGrant + instGrant + scholarship + workStudy;

        // Build optional context lines
        StringBuilder profile = new StringBuilder();
        if (req.getFirstGeneration() != null && req.getFirstGeneration())
            profile.append("- First-generation college student\n");
        if (req.getGender() != null && !req.getGender().isBlank())
            profile.append("- Gender: ").append(req.getGender()).append("\n");
        if (req.getRace() != null && !req.getRace().isBlank())
            profile.append("- Race/Ethnicity: ").append(req.getRace()).append("\n");
        if (req.getGpa() != null)
            profile.append("- GPA: ").append(req.getGpa()).append("\n");
        if (req.getExtracurriculars() != null && !req.getExtracurriculars().isBlank())
            profile.append("- Extracurricular activities: ").append(req.getExtracurriculars()).append("\n");
        if (req.getAcademicAchievements() != null && !req.getAcademicAchievements().isBlank())
            profile.append("- Academic achievements: ").append(req.getAcademicAchievements()).append("\n");

        return String.format("""
            You are a financial literacy advisor helping undergraduate students make responsible decisions about student loans and maximizing their aid opportunities.

            Student profile for ONE academic year at %s%s:
            %s
            FAFSA Aid Package:
            - Pell Grant (federal, free): $%.2f
            - Subsidized Federal Loan (need-based, no interest while enrolled): $%.2f
            - Unsubsidized Federal Loan: $%.2f
            - Parent PLUS Loan: $%.2f
            - Institutional Grant: $%.2f
            - Scholarship / Gift Aid: $%.2f
            - Federal Work-Study: $%.2f
            - Total loans this year: $%.2f
            - Total free aid this year: $%.2f
            %s
            %s

            Please provide clear, practical guidance on:
            1. Whether this loan amount is reasonable given the projected earnings
            2. Key risks to be aware of (interest accrual on unsubsidized loans, Parent PLUS repayment burden, etc.)
            3. Strategies to minimize total debt accumulation over all 4 years of college, including how borrowing choices each year compound into the full repayment burden at graduation
            4. Research scholarship opportunities specifically for this student based on their GPA, demographics (gender, race/ethnicity, first-generation status), extracurricular activities, and academic achievements. Provide the names of 2 scholarships likely offered or promoted by %s (the college itself) with their direct scholarship page URL.
            5. Suggest 2-3 specific part-time employment opportunities directly related to %s that this student could realistically do while in school to offset costs (be specific to this field, not generic suggestions)

            Keep the response concise, supportive, and easy to understand for an 18-22 year old undergraduate.
            """,
            req.getCollegeName() != null ? req.getCollegeName() : "this college",
            req.getMajor() != null ? ", pursuing " + req.getMajor() : "",
            profile.toString(),
            pellGrant, subsidized, unsubsidized, parentPlus, instGrant, scholarship, workStudy,
            totalLoans, totalFreeAid,
            req.getNetPrice() != null ? String.format("- College Net Price: $%.2f/yr", req.getNetPrice()) : "",
            req.getSixYrEarnings() != null ? String.format("- Median earnings 6 years after graduation: $%.2f/yr", req.getSixYrEarnings()) : "",
            req.getCollegeName() != null ? req.getCollegeName() : "this college",
            req.getMajor() != null ? req.getMajor() : "their chosen major"
        );
    }
}
