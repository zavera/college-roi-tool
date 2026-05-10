package com.example.collegeroitool.service;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.dto.PremiumInsightsRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private String summaryPromptTemplate;
    private String insightsPromptTemplate;

    private final RestTemplate restTemplate;

    public GroqService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void loadPromptTemplates() throws IOException {
        summaryPromptTemplate = new String(
            new ClassPathResource("prompts/summary-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        insightsPromptTemplate = new String(
            new ClassPathResource("prompts/insights-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }

    // ── Free AI Summary ───────────────────────────────────────────────────────

    public String getFinancialAdvice(LlmAdviceRequest req) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return buildDevStubAdvice(req);
        }
        String prompt = buildSummaryPrompt(req);
        return callGroq(prompt, 1800, 0.2);
    }

    private String buildSummaryPrompt(LlmAdviceRequest req) {
        double federalLoan = (req.getSubsidizedLoan()    != null ? req.getSubsidizedLoan()    : 0)
                           + (req.getUnsubsidizedLoan()  != null ? req.getUnsubsidizedLoan()  : 0);
        double parentPlus  =  req.getParentPlusLoan()    != null ? req.getParentPlusLoan()    : 0;
        double pellGrant   =  req.getPellGrant()          != null ? req.getPellGrant()          : 0;
        double instGrant   =  req.getInstitutionalGrant() != null ? req.getInstitutionalGrant() : 0;
        double scholarship =  req.getScholarshipAmount()  != null ? req.getScholarshipAmount()  : 0;
        double workStudy   =  req.getWorkStudy()          != null ? req.getWorkStudy()          : 0;

        double netPrice  = req.getComputedNetPrice()  != null ? req.getComputedNetPrice()
                         : (req.getNetPrice()         != null ? req.getNetPrice() : 0);
        double unmetNeed = req.getComputedUnmetNeed() != null ? req.getComputedUnmetNeed() : 0;
        double coa       = req.getComputedCOA()       != null ? req.getComputedCOA()       : 0;
        double majorEarnings       = req.getSixYrEarnings()         != null ? req.getSixYrEarnings()       : 0;
        double collegeWideEarnings = req.getCollegeWideEarnings()   != null ? req.getCollegeWideEarnings() : majorEarnings;

        String collegeName = req.getCollegeName()     != null ? req.getCollegeName()     : "this college";
        String major       = req.getMajor()           != null ? req.getMajor()           : "Undecided";
        String residency   = req.getResidency()       != null ? req.getResidency()       : "instate";
        String living      = req.getLivingSituation() != null ? req.getLivingSituation() : "oncampus";

        return summaryPromptTemplate
            .replace("{{collegeName}}", collegeName)
            .replace("{{major}}", major)
            .replace("{{residency}}", residency)
            .replace("{{living}}", living)
            .replace("{{coa}}",               fmt(coa))
            .replace("{{netPrice}}",          fmt(netPrice))
            .replace("{{unmetNeed}}",         fmt(unmetNeed))
            .replace("{{federalLoan}}",       fmt(federalLoan))
            .replace("{{parentPlus}}",        fmt(parentPlus))
            .replace("{{pellGrant}}",         fmt(pellGrant))
            .replace("{{instGrant}}",         fmt(instGrant))
            .replace("{{scholarship}}",       fmt(scholarship))
            .replace("{{workStudy}}",         fmt(workStudy))
            .replace("{{majorEarnings}}",     fmt(majorEarnings))
            .replace("{{collegeWideEarnings}}", fmt(collegeWideEarnings));
    }

    private String buildDevStubAdvice(LlmAdviceRequest req) {
        String college = req.getCollegeName() != null ? req.getCollegeName() : "Sample University";
        String major   = req.getMajor()       != null ? req.getMajor()       : "Computer Science";
        String collegeSlug = college.toLowerCase().replace(" ", "");

        return """
            {
              "schoolMajorResources": {
                "professionalSocieties": [
                  {"name": "[DEV] Association for Computing Machinery (ACM) — %s Chapter", "description": "Student chapter hosting weekly coding competitions, guest speakers from industry, and annual hackathon. Open to all CS and related majors. Meetings Thursdays 6pm in Engineering Hall."},
                  {"name": "[DEV] Institute of Electrical and Electronics Engineers (IEEE) — %s Student Branch", "description": "Hands-on project teams in robotics, embedded systems, and circuit design. Connects members with internship pipelines at Boeing, Intel, and regional tech firms."},
                  {"name": "[DEV] National Society of Black Engineers (NSBE) — %s Chapter", "description": "Academic excellence and professional development for underrepresented engineering and tech students. Hosts resume workshops and the annual NSBE Regional Conference."}
                ],
                "freshmanResources": [
                  {"name": "[DEV] First-Year Experience (FYE) Office — %s", "description": "Dedicated staff helping new students navigate registration, campus life, and academic goal-setting. Drop-in hours Monday–Friday 9am–4pm in the Student Success Center, Room 110."},
                  {"name": "[DEV] Financial Aid Appeals & Special Circumstances — %s Office of Financial Aid", "description": "Submit a Special Circumstances appeal if your family income has changed since filing the FAFSA. Average additional award for successful appeals: $2,000–$4,000. Visit finaid.%s.edu or call the aid office."},
                  {"name": "[DEV] Early Alert Academic Support — %s", "description": "Faculty submit early alerts for students showing signs of academic struggle. Triggers peer tutoring, supplemental instruction, and academic coaching before midterms."},
                  {"name": "[DEV] Student Emergency Fund — %s", "description": "One-time grants up to $500 for unexpected financial hardship. Apply online through the Dean of Students portal; decisions within 48 hours."},
                  {"name": "[DEV] Honors College & Scholarship Advising — %s", "description": "All freshmen can meet with scholarship advisors for Goldwater, Fulbright, and national fellowship guidance. Office hours by appointment in the Honors House."}
                ]
              },
              "keyConsiderations": [
                {"title": "[DEV] Federal Loan Sustainability vs. Income Threshold", "body": "Borrowing $5,500/yr in federal loans over 4 years totals $22,000. At 6.5%% over 10 years, your monthly payment is approximately $249/month ($2,988/yr) — about 5-6%% of a $55,000 median starting salary. Keep total debt at graduation at or below one year's projected starting salary."},
                {"title": "[DEV] Unsubsidized Loan Interest Accrual", "body": "Unsubsidized loan interest starts accruing the day funds are disbursed. On $3,500 unsubsidized at 6.5%% over 4 years, approximately $910 in interest capitalizes before your first payment — increasing your effective balance at graduation."},
                {"title": "[DEV] Unmet Need Gap Strategy", "body": "With an estimated $8,000/yr unmet need, your 4-year gap is ~$32,000. Options: (1) submit a written financial aid appeal citing income changes; (2) apply for 3–5 external scholarships before sophomore year; (3) 10–15 hrs/week part-time at $14/hr yields ~$7,000/yr."},
                {"title": "[DEV] Merit Aid GPA Maintenance", "body": "Most institutional grants and merit scholarships require a minimum 3.0–3.25 cumulative GPA. A single difficult semester can suspend your award. Contact the financial aid office proactively if your GPA dips — early communication often allows a one-semester grace period."},
                {"title": "[DEV] Parent PLUS Loan Consideration", "body": "PLUS loans carry a 9.08%% interest rate vs. 6.5%% for federal student loans and begin repayment immediately unless deferred. If PLUS borrowing is necessary, request income-contingent deferment and prioritize paying it down before compounding reaches Year 4."}
              ]
            }
            """.formatted(college, college, college, college, college, collegeSlug, college, college, college);
    }

    // ── Premium Scholarship & Employment Intelligence ─────────────────────────

    public String getPremiumInsights(PremiumInsightsRequest req) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return buildDevStubInsights(req);
        }
        String prompt = buildInsightsPrompt(req);
        return callGroq(prompt, 700, 0.4);
    }

    private String buildInsightsPrompt(PremiumInsightsRequest req) {
        String collegeName = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major       = req.getMajor()       != null ? req.getMajor()       : "your major";
        String majorSuffix = req.getMajor()       != null ? ", pursuing " + req.getMajor() : "";
        String netPriceLine  = req.getNetPrice()      != null
            ? "Net price (after institutional aid): $" + fmt(req.getNetPrice()) + "/yr." : "";
        String earningsLine  = req.getSixYrEarnings() != null
            ? "Median earnings 6 years after graduation: $" + fmt(req.getSixYrEarnings()) + "/yr." : "";

        return insightsPromptTemplate
            .replace("{{collegeName}}", collegeName)
            .replace("{{major}}", major)
            .replace("{{majorSuffix}}", majorSuffix)
            .replace("{{netPriceLine}}", netPriceLine)
            .replace("{{earningsLine}}", earningsLine);
    }

    private String buildDevStubInsights(PremiumInsightsRequest req) {
        String major = req.getMajor() != null ? req.getMajor() : "your major";
        return """
            {
              "fieldSpecificScholarships": [
                {"name": "[DEV] Google Generation Scholarship", "amount": "$10,000", "sponsor": "Google", "eligibility": "CS or related field; underrepresented in tech; 3.0+ GPA", "deadline": "December annually", "url": "https://buildyourfuture.withgoogle.com/scholarships"},
                {"name": "[DEV] Microsoft Tuition Scholarship", "amount": "$5,000", "sponsor": "Microsoft", "eligibility": "Freshman–senior in CS or STEM; financial need", "deadline": "January annually", "url": "https://careers.microsoft.com/students/us/en/usscholarship"},
                {"name": "[DEV] AFCEA STEM Scholarship", "amount": "$2,500–$5,000", "sponsor": "AFCEA", "eligibility": "US citizen; STEM sophomore or above; 3.0+ GPA", "deadline": "February 28 annually", "url": "https://www.afcea.org/education/scholarships"}
              ],
              "employment": [
                {"title": "[DEV] Undergraduate Research Assistant — %s Dept.", "pay": "Volunteer for Credit / $13–$15/hr", "type": "Undergraduate Research", "details": "Faculty in the %s program are actively recruiting research assistants each semester. Volunteer-for-credit positions count toward graduation requirements.", "url": "https://app.joinhandshake.com/"},
                {"title": "[DEV] Department Teaching Assistant / Tutor", "pay": "$12–$14/hr", "type": "On-Campus Employment", "details": "On-campus TA roles for %s students are posted each term. Federal work-study eligible — earnings do not affect next year's aid calculation.", "url": "https://app.joinhandshake.com/"},
                {"title": "[DEV] Co-op / Practicum Placement", "pay": "$17–$22/hr", "type": "Co-op / Internship", "details": "Alternating-semester co-op in %s-related firms near campus. Converts to full-time offers at a 60%% rate.", "url": "https://app.joinhandshake.com/"}
              ],
              "yearlyTips": [
                "File FAFSA in October and submit a written aid appeal — keep federal loans at or below $5,500 in Year 1.",
                "Request a merit aid review if your GPA improved; explore department research stipends to offset Year 2 cost increases.",
                "Apply for upperclassman scholarships (fewer applicants); use co-op income to reduce Parent PLUS dependency.",
                "Maximize work-study and confirm senior-year GPA qualifies you for post-grad loan forgiveness programs."
              ]
            }
            """.formatted(major, major, major, major);
    }

    // ── Shared Groq caller ────────────────────────────────────────────────────

    private String callGroq(String prompt, int maxTokens, double temperature) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            apiUrl, new HttpEntity<>(body, headers), Map.class);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        return (String) msg.get("content");
    }

    private static String fmt(double value) {
        return String.format("%.0f", value);
    }
}
