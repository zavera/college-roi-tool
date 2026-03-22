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
        double subsidized   = req.getSubsidizedLoan()    != null ? req.getSubsidizedLoan()    : 0;
        double unsubsidized = req.getUnsubsidizedLoan()  != null ? req.getUnsubsidizedLoan()  : 0;
        double familyCont   = req.getParentPlusLoan()    != null ? req.getParentPlusLoan()    : 0;
        double pellGrant    = req.getPellGrant()          != null ? req.getPellGrant()          : 0;
        double instGrant    = req.getInstitutionalGrant() != null ? req.getInstitutionalGrant(): 0;
        double scholarship  = req.getScholarshipAmount()  != null ? req.getScholarshipAmount() : 0;
        double workStudy    = req.getWorkStudy()          != null ? req.getWorkStudy()          : 0;
        double totalLoans   = subsidized + unsubsidized;
        double totalFreeAid = pellGrant + instGrant + scholarship;

        // Residency label
        String residencyLabel = "In-State";
        if (req.getResidency() != null) {
            switch (req.getResidency()) {
                case "outofstate"   -> residencyLabel = "Out-of-State";
                case "international"-> residencyLabel = "International";
                case "online"       -> residencyLabel = "Online / Distance Learning";
            }
        }

        // Living situation label
        String livingLabel = "On-Campus";
        if (req.getLivingSituation() != null) {
            switch (req.getLivingSituation()) {
                case "offcampus" -> livingLabel = "Off-Campus Apartment";
                case "home"      -> livingLabel = "At Home with Family";
            }
        }

        // Cost flags for tone calibration
        boolean isOutOfState   = "outofstate".equals(req.getResidency()) || "international".equals(req.getResidency());
        double  computedCOA    = req.getComputedCOA()       != null ? req.getComputedCOA()       : 0;
        double  computedNet    = req.getComputedNetPrice()   != null ? req.getComputedNetPrice()   : 0;
        double  unmetNeed      = req.getComputedUnmetNeed()  != null ? req.getComputedUnmetNeed()  : 0;
        boolean highNetPrice   = computedNet    > 30000;
        boolean highUnmetNeed  = unmetNeed      > 15000;
        boolean loansAreDriver = totalLoans > 0 && (totalLoans / Math.max(computedNet, 1)) > 0.4;

        // Build student profile block
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

        // Tone instruction based on risk flags
        StringBuilder toneInstruction = new StringBuilder();
        if (loansAreDriver) toneInstruction.append("IMPORTANT: Loans are the primary mechanism covering this student's costs — use a cautious, alert tone throughout.\n");
        if (highNetPrice && highUnmetNeed) toneInstruction.append("IMPORTANT: Both Net Price and Unmet Need are high — flag this as a significant financial risk.\n");
        if (isOutOfState) toneInstruction.append("IMPORTANT: This student is attending as an out-of-state/international student — factor in whether residency choice is the primary cost driver.\n");

        String college   = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String majorStr  = req.getMajor()       != null ? req.getMajor()       : "their chosen major";

        return String.format("""
            You are a financial literacy advisor helping undergraduate students make responsible decisions about student loans. Your primary goal is to help students borrow as little as possible.

            %s
            Student profile for ONE academic year at %s%s:
            %s
            Cost of Attendance (based on student's selections):
            - Residency status: %s
            - Living situation: %s
            - Estimated Total COA / Year: $%.2f
            - Net Price (COA minus free gift aid): $%.2f
            - Unmet Need (Net Price minus loans, work-study, and family contribution): $%.2f

            FAFSA Aid Package:
            - Pell Grant (free, no repayment): $%.2f
            - Institutional Grant (free, no repayment): $%.2f
            - Scholarship / Gift Aid (free, no repayment): $%.2f
            - Subsidized Federal Loan (need-based, no interest while enrolled): $%.2f
            - Unsubsidized Federal Loan (interest accrues immediately): $%.2f
            - Federal Work-Study: $%.2f
            - Family Contribution: $%.2f
            - Total loans this year: $%.2f
            - Total free aid this year: $%.2f
            %s

            Please provide clear, practical guidance on the following — lead with the most important financial concern first:
            1. Whether the total loan amount is reasonable relative to projected earnings. Compare estimated 4-year total loan burden to the median first-year salary.
            2. **Net Price & Unmet Need Analysis**: Evaluate this student's financial position critically.
               - If both Net Price and Unmet Need are high, flag this as a significant financial risk and urge caution.
               - If Unmet Need is low but loans are still high, flag the risk of over-borrowing beyond what is necessary.
               - If the student is Out-of-State or International, specifically assess whether the residency choice is the primary cost driver and whether switching to an in-state or online program would significantly reduce the burden.
               - Use the most cautious tone when loans are the primary mechanism covering costs.
            3. Strategies to minimize total debt over all 4 years, including how each year's borrowing compounds into the full repayment burden at graduation.
            4. Research 2 scholarship opportunities specifically for this student based on their GPA, demographics, and extracurriculars. Provide the scholarship name and its direct URL as a markdown hyperlink in the format [Scholarship Name](URL), linking to the actual scholarship page at %s or a well-known external source.
            5. Suggest 2-3 specific part-time employment opportunities directly related to %s that this student could realistically do while in school to offset costs.

            Keep the response concise, supportive, and easy to understand for an 18-22 year old undergraduate.
            """,
            toneInstruction.toString(),
            college,
            req.getMajor() != null ? ", pursuing " + req.getMajor() : "",
            profile.toString(),
            residencyLabel, livingLabel,
            computedCOA, computedNet, unmetNeed,
            pellGrant, instGrant, scholarship, subsidized, unsubsidized, workStudy, familyCont,
            totalLoans, totalFreeAid,
            req.getSixYrEarnings() != null ? String.format("- Median earnings 6 years after graduation: $%.2f/yr", req.getSixYrEarnings()) : "",
            college, majorStr
        );
    }
}
