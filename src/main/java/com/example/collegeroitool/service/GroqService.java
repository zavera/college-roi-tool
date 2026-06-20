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
    private String scholarshipPromptTemplate;
    private String employmentPromptTemplate;
    private String costplanPromptTemplate;
    private String campusPromptTemplate;
    private String counselorPromptTemplate;
    private String repaymentPromptTemplate;
    private String hardshipPromptTemplate;
    private String privateHardshipPromptTemplate;
    private String fafsaReadinessPromptTemplate;
    private String fafsaRoadmapPromptTemplate;
    private String fafsaChatPromptTemplate;
    private String fafsaAssetRepositioningPromptTemplate;
    private String fafsaPjAppealPromptTemplate;
    private String fafsaSaiCommentaryPromptTemplate;
    private String fafsaCssExplainerPromptTemplate;
    private String institutionalChatPromptTemplate;
    private String astraChatPromptTemplate;

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
        scholarshipPromptTemplate = new String(
            new ClassPathResource("prompts/scholarship-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        employmentPromptTemplate = new String(
            new ClassPathResource("prompts/employment-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        costplanPromptTemplate = new String(
            new ClassPathResource("prompts/costplan-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        campusPromptTemplate = new String(
            new ClassPathResource("prompts/campus-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        counselorPromptTemplate = new String(
            new ClassPathResource("prompts/counselor-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        repaymentPromptTemplate = new String(
            new ClassPathResource("prompts/repayment-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        hardshipPromptTemplate = new String(
            new ClassPathResource("prompts/hardship-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        privateHardshipPromptTemplate = new String(
            new ClassPathResource("prompts/private-hardship-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        fafsaReadinessPromptTemplate = new String(
            new ClassPathResource("prompts/fafsa-readiness-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        fafsaRoadmapPromptTemplate = new String(
            new ClassPathResource("prompts/fafsa-roadmap-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        fafsaChatPromptTemplate = new String(
            new ClassPathResource("prompts/fafsa-chat-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        fafsaAssetRepositioningPromptTemplate = new String(
            new ClassPathResource("prompts/fafsa-asset-repositioning-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        fafsaPjAppealPromptTemplate = new String(
            new ClassPathResource("prompts/fafsa-pj-appeal-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        fafsaSaiCommentaryPromptTemplate = new String(
            new ClassPathResource("prompts/fafsa-sai-commentary-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        fafsaCssExplainerPromptTemplate = new String(
            new ClassPathResource("prompts/fafsa-css-explainer-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        institutionalChatPromptTemplate = new String(
            new ClassPathResource("prompts/institutional-chat-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        astraChatPromptTemplate = new String(
            new ClassPathResource("prompts/chat-prompt.txt").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }

    // ── Free AI Summary ───────────────────────────────────────────────────────

    public String getFinancialAdvice(LlmAdviceRequest req) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return buildDevStubAdvice(req);
        }
        String prompt = buildSummaryPrompt(req);
        return callGroq(prompt, 2600, 0.2);
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
                  {"name": "Association for Computing Machinery (ACM) -- %s Chapter", "description": "Student chapter hosting weekly coding competitions, guest speakers from industry, and annual hackathon. Open to all CS and related majors. Meetings Thursdays 6pm in Engineering Hall."},
                  {"name": "Institute of Electrical and Electronics Engineers (IEEE) -- %s Student Branch", "description": "Hands-on project teams in robotics, embedded systems, and circuit design. Connects members with internship pipelines at Boeing, Intel, and regional tech firms."},
                  {"name": "National Society of Black Engineers (NSBE) -- %s Chapter", "description": "Academic excellence and professional development for underrepresented engineering and tech students. Hosts resume workshops and the annual NSBE Regional Conference."}
                ],
                "freshmanResources": [
                  {"name": "First-Year Experience (FYE) Office -- %s", "description": "Dedicated staff helping new students navigate registration, campus life, and academic goal-setting. Drop-in hours Monday-Friday 9am-4pm in the Student Success Center, Room 110."},
                  {"name": "Financial Aid Appeals & Special Circumstances -- %s Office of Financial Aid", "description": "Submit a Special Circumstances appeal if your family income has changed since filing the FAFSA. Average additional award for successful appeals: $2,000-$4,000. Visit finaid.%s.edu or call the aid office."},
                  {"name": "Early Alert Academic Support -- %s", "description": "Faculty submit early alerts for students showing signs of academic struggle. Triggers peer tutoring, supplemental instruction, and academic coaching before midterms."},
                  {"name": "Student Emergency Fund -- %s", "description": "One-time grants up to $500 for unexpected financial hardship. Apply online through the Dean of Students portal; decisions within 48 hours."},
                  {"name": "Honors College & Scholarship Advising -- %s", "description": "All freshmen can meet with scholarship advisors for Goldwater, Fulbright, and national fellowship guidance. Office hours by appointment in the Honors House."}
                ]
              },
              "scholarships": [
                {"name": "%s Foundation Merit Scholarship", "amount": "$3,000/yr", "sponsor": "%s Alumni Foundation", "eligibility": "3.0+ GPA, full-time enrollment", "deadline": "March 1", "notes": "Renewable all 4 years"},
                {"name": "%s Department Excellence Award for %s", "amount": "$2,500", "sponsor": "%s Department", "eligibility": "Declared %s major, sophomore or above, 3.2+ GPA", "deadline": "February 15", "notes": "One per department per year"},
                {"name": "%s Need-Based Supplemental Grant", "amount": "$1,500/yr", "sponsor": "%s Office of Financial Aid", "eligibility": "EFC below $15,000, full-time enrollment", "deadline": "Rolling", "notes": "Complete FAFSA early; awarded each semester"},
                {"name": "Professional Association Scholarship for %s Students", "amount": "$2,000", "sponsor": "%s Professional Association", "eligibility": "Active student member, essay required", "deadline": "January 31", "notes": "Submit a 2-page portfolio"},
                {"name": "Local Community Foundation STEM Award", "amount": "$1,500", "sponsor": "City Community Foundation", "eligibility": "Metro-area resident, STEM field, demonstrated need", "deadline": "April 1", "notes": "200-word essay required"}
              ],
              "employmentOpportunities": [
                {"title": "Undergraduate Research Assistant -- %s Dept.", "type": "Undergraduate Research", "pay": "Volunteer for Credit / $13-$15/hr", "details": "Faculty in the %s program recruit research assistants each semester. Ask your academic advisor or check the department bulletin board; volunteer-for-credit counts toward graduation requirements."},
                {"title": "Department Teaching Assistant / Tutor", "type": "On-Campus Employment", "pay": "$12-$14/hr", "details": "TA roles for %s courses posted each term on Handshake. Federal work-study eligible -- earnings do not affect FAFSA EFC."},
                {"title": "Co-op / Practicum at %s-area Firms", "type": "Co-op / Internship", "pay": "$17-$22/hr", "details": "Alternating-semester co-ops through the %s Career Center. 60%% employer conversion rate to full-time. Register on Handshake and attend the Fall Employer Fair in September."},
                {"title": "Campus IT / Library Student Worker", "type": "On-Campus Employment", "pay": "$13/hr", "details": "Technical support and library positions available each semester. Flexible scheduling; ideal for first-year students. Apply at the Student Employment Office."}
              ],
              "keyConsiderations": [
                {"title": "Federal Loan Sustainability vs. Income Threshold", "body": "Borrowing $5,500/yr in federal loans over 4 years totals $22,000. At 6.5%% over 10 years, your monthly payment is approximately $249/month ($2,988/yr) -- about 5-6%% of a $55,000 median starting salary. Keep total debt at graduation at or below one year's projected starting salary."},
                {"title": "Unsubsidized Loan Interest Accrual", "body": "Unsubsidized loan interest starts accruing the day funds are disbursed. On $3,500 unsubsidized at 6.5%% over 4 years, approximately $910 in interest capitalizes before your first payment -- increasing your effective balance at graduation."},
                {"title": "Unmet Need Gap Strategy", "body": "With an estimated $8,000/yr unmet need, your 4-year gap is ~$32,000. Options: (1) submit a written financial aid appeal citing income changes; (2) apply for 3-5 external scholarships before sophomore year; (3) 10-15 hrs/week part-time at $14/hr yields ~$7,000/yr."},
                {"title": "Merit Aid GPA Maintenance", "body": "Most institutional grants and merit scholarships require a minimum 3.0-3.25 cumulative GPA. A single difficult semester can suspend your award. Contact the financial aid office proactively if your GPA dips -- early communication often allows a one-semester grace period."},
                {"title": "Parent PLUS Loan Consideration", "body": "PLUS loans carry a 9.08%% interest rate vs. 6.5%% for federal student loans and begin repayment immediately unless deferred. If PLUS borrowing is necessary, request income-contingent deferment and prioritize paying it down before compounding reaches Year 4."}
              ]
            }
            """.formatted(
                college, college, college,
                college, college, collegeSlug, college, college, college,
                college, college,
                college, major, college, major,
                college, college,
                major, major,
                major, major,
                college, major, college,
                major, college
            );
    }

    // ── Premium Scholarship & Employment Intelligence ─────────────────────────

    public String getPremiumInsights(PremiumInsightsRequest req) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return buildDevStubInsights(req);
        }
        String section = req.getSection() != null ? req.getSection() : "";
        switch (section) {
            case "scholarships": return callGroq(buildScholarshipPrompt(req), 700,  0.3);
            case "employment":   return callGroq(buildEmploymentPrompt(req),  900,  0.3);
            case "costplan":     return callGroq(buildCostplanPrompt(req),    800,  0.3);
            case "campus":       return callGroq(buildCampusPrompt(req),      600,  0.3);
            case "counselor":    return callGroq(buildCounselorPrompt(req),  1100,  0.2);
            default:             return callGroq(buildInsightsPrompt(req),   2400,  0.4);
        }
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

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

    private String buildScholarshipPrompt(PremiumInsightsRequest req) {
        String collegeName  = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major        = req.getMajor()       != null ? req.getMajor()       : "your major";
        String majorSuffix  = req.getMajor()       != null ? ", pursuing " + req.getMajor() : "";
        String netPriceLine = req.getNetPrice()      != null
            ? "Net price: $" + fmt(req.getNetPrice()) + "/yr." : "";
        String earningsLine = req.getSixYrEarnings() != null
            ? "Median 6-year earnings: $" + fmt(req.getSixYrEarnings()) + "/yr." : "";

        return scholarshipPromptTemplate
            .replace("{{collegeName}}", collegeName)
            .replace("{{major}}", major)
            .replace("{{majorSuffix}}", majorSuffix)
            .replace("{{netPriceLine}}", netPriceLine)
            .replace("{{earningsLine}}", earningsLine)
            .replace("{{demographicLine}}", buildDemographicLine(req));
    }

    private String buildEmploymentPrompt(PremiumInsightsRequest req) {
        String collegeName  = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major        = req.getMajor()       != null ? req.getMajor()       : "your major";
        String earningsLine = req.getSixYrEarnings() != null
            ? "Median 6-year earnings: $" + fmt(req.getSixYrEarnings()) + "/yr." : "";

        return employmentPromptTemplate
            .replace("{{collegeName}}", collegeName)
            .replace("{{major}}", major)
            .replace("{{earningsLine}}", earningsLine);
    }

    private String buildCostplanPrompt(PremiumInsightsRequest req) {
        String collegeName  = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major        = req.getMajor()       != null ? req.getMajor()       : "your major";
        String netPriceLine = req.getNetPrice()      != null
            ? "Net price: $" + fmt(req.getNetPrice()) + "/yr." : "";
        String earningsLine = req.getSixYrEarnings() != null
            ? "Median 6-year earnings: $" + fmt(req.getSixYrEarnings()) + "/yr." : "";
        String gpaStr       = req.getGpa() != null ? String.format("%.2f", req.getGpa()) : "not provided";
        String firstGenStr  = req.getFirstGen() != null ? (req.getFirstGen() ? "Yes" : "No") : "not provided";
        String achievements = req.getAcademicAchievements() != null && !req.getAcademicAchievements().isBlank()
            ? req.getAcademicAchievements() : "not provided";
        String extracurr   = req.getExtracurriculars() != null && !req.getExtracurriculars().isBlank()
            ? req.getExtracurriculars() : "not provided";
        String loanAmt     = req.getLoanAmount() != null ? fmt(req.getLoanAmount()) : "0";

        return costplanPromptTemplate
            .replace("{{collegeName}}", collegeName)
            .replace("{{major}}", major)
            .replace("{{netPriceLine}}", netPriceLine)
            .replace("{{earningsLine}}", earningsLine)
            .replace("{{demographicLine}}", buildDemographicLine(req))
            .replace("{{gpa}}", gpaStr)
            .replace("{{firstGen}}", firstGenStr)
            .replace("{{achievements}}", achievements)
            .replace("{{extracurriculars}}", extracurr)
            .replace("{{loanAmount}}", loanAmt);
    }

    private String buildCampusPrompt(PremiumInsightsRequest req) {
        String collegeName = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major       = req.getMajor()       != null ? req.getMajor()       : "your major";

        return campusPromptTemplate
            .replace("{{collegeName}}", collegeName)
            .replace("{{major}}", major);
    }

    private String buildCounselorPrompt(PremiumInsightsRequest req) {
        String collegeName  = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major        = req.getMajor()       != null ? req.getMajor()       : "your major";
        String netPriceLine = req.getNetPrice()      != null
            ? "Net price: $" + fmt(req.getNetPrice()) + "/yr." : "";
        String earningsLine = req.getSixYrEarnings() != null
            ? "Median 6-year earnings: $" + fmt(req.getSixYrEarnings()) + "/yr." : "";
        String gpaStr       = req.getGpa() != null ? String.format("%.2f", req.getGpa()) : "not provided";
        String firstGenStr  = req.getFirstGen() != null ? (req.getFirstGen() ? "Yes" : "No") : "not provided";
        String achievements = req.getAcademicAchievements() != null && !req.getAcademicAchievements().isBlank()
            ? req.getAcademicAchievements() : "not provided";
        String loanAmt     = req.getLoanAmount() != null ? fmt(req.getLoanAmount()) : "0";

        return counselorPromptTemplate
            .replace("{{collegeName}}", collegeName)
            .replace("{{major}}", major)
            .replace("{{demographicLine}}", buildDemographicLine(req))
            .replace("{{gpa}}", gpaStr)
            .replace("{{firstGen}}", firstGenStr)
            .replace("{{achievements}}", achievements)
            .replace("{{netPriceLine}}", netPriceLine)
            .replace("{{earningsLine}}", earningsLine)
            .replace("{{loanAmount}}", loanAmt);
    }

    /** Build a human-readable demographic summary from optional profile fields. */
    private String buildDemographicLine(PremiumInsightsRequest req) {
        List<String> parts = new ArrayList<>();
        if (req.getGender() != null && !req.getGender().isBlank()) {
            parts.add("Gender: " + req.getGender());
        }
        if (req.getRace() != null && !req.getRace().isBlank()) {
            parts.add("Race/Ethnicity: " + req.getRace());
        }
        if (req.getFirstGen() != null) {
            parts.add("First-generation: " + (req.getFirstGen() ? "Yes" : "No"));
        }
        return parts.isEmpty() ? "Not provided" : String.join(", ", parts);
    }

    // ── Dev stubs ─────────────────────────────────────────────────────────────

    private String buildDevStubInsights(PremiumInsightsRequest req) {
        String section = req.getSection() != null ? req.getSection() : "";
        String college = req.getCollegeName() != null ? req.getCollegeName() : "Sample University";
        String major   = req.getMajor()       != null ? req.getMajor()       : "Computer Science";

        switch (section) {
            case "scholarships":
                return """
                    {"scholarships":[
                      {"name":"%s Foundation Merit Award","amount":"$3,000","sponsor":"%s Alumni Foundation","eligibility":"3.0+ GPA, enrolled full-time","deadline":"March 1","notes":"Renewable for 4 years","url":"https://app.joinhandshake.com/"},
                      {"name":"%s Departmental Excellence Scholarship","amount":"$2,500","sponsor":"%s Department of %s","eligibility":"Declared %s major, sophomore or above","deadline":"February 15","notes":"One per department","url":"https://app.joinhandshake.com/"},
                      {"name":"Local Community Foundation STEM Award","amount":"$1,500","sponsor":"City Community Foundation","eligibility":"Resident of metro area, STEM field","deadline":"April 1","notes":"Essay required","url":"https://app.joinhandshake.com/"},
                      {"name":"Professional Association Scholarship","amount":"$2,000","sponsor":"%s Professional Association","eligibility":"Active student member","deadline":"January 31","notes":"Submit portfolio","url":"https://app.joinhandshake.com/"},
                      {"name":"Need-Based Supplemental Grant","amount":"$1,000","sponsor":"%s Financial Aid Office","eligibility":"EFC below $10,000, enrolled full-time","deadline":"Rolling","notes":"Awarded each semester","url":"https://app.joinhandshake.com/"}
                    ]}""".formatted(college, college, college, college, major, major, major, college);

            case "employment":
                return """
                    {"trajectory":"%s graduates build expertise in analytical and applied domains, often beginning in entry-level analyst or associate roles before advancing to specialist and leadership positions. The field rewards continuous learning and networking through professional associations.","typicalRoles":[
                      {"year":"Entry (Year 1-2)","title":"Junior %s Analyst","salary":"$45,000-$58,000","description":"Foundation roles in established organizations; building core technical and communication skills."},
                      {"year":"Early Career (Year 3-5)","title":"%s Specialist","salary":"$58,000-$78,000","description":"Taking ownership of projects; mentoring interns and junior staff; expanding domain expertise."},
                      {"year":"Mid-Career (Year 6-10)","title":"Senior %s Professional","salary":"$78,000-$105,000","description":"Strategic leadership; cross-functional collaboration; potential people management track."},
                      {"year":"Senior (Year 10+)","title":"Director / Principal %s","salary":"$105,000-$150,000+","description":"Organizational strategy; budget authority; industry thought leadership."}
                    ],"industries":["Technology","Healthcare","Finance","Government"],"outlook":"Demand for %s professionals remains strong through 2027, with above-average growth projected in the technology and healthcare sectors."}""".formatted(major, major, major, major, major, major);

            case "costplan":
                return """
                    {"yearlyTips":[
                      "Year 1: File FAFSA in October and submit a written aid appeal. Limit federal loans to $5,500 and explore work-study positions in the %s department.",
                      "Year 2: Request a merit aid review if GPA improved. Apply for upperclassman scholarships and explore paid research assistant roles.",
                      "Year 3: Secure a co-op or internship to generate $8,000-$15,000 toward Year 4 costs. Apply for professional association awards in %s.",
                      "Year 4: Minimize new loans; use senior-year departmental grants. Confirm post-grad income-driven repayment eligibility."
                    ],"demographicStrategies":[
                      "Apply for the %s Departmental Diversity Scholarship by February 15 - matches your demographic profile.",
                      "Contact the financial aid office about first-generation student supplemental grants; average award $1,200/yr.",
                      "Submit a Special Circumstances appeal if family income changed since last FAFSA filing."
                    ],"appealTip":"Request a professional judgment review at %s, citing your academic record and any extenuating financial circumstances in writing. Reference competing offers from peer institutions if available."}""".formatted(major, major, major, college);

            case "campus":
                return """
                    {"employment":[
                      {"title":"Undergraduate Research Assistant - %s Dept.","pay":"Volunteer for Credit / $13-$15/hr","type":"Undergraduate Research","details":"Faculty in the %s program recruit research assistants each semester. Volunteer-for-credit positions count toward graduation requirements. Ask your academic advisor or check the department bulletin board.","url":"https://app.joinhandshake.com/"},
                      {"title":"Department Teaching Assistant / Tutor","pay":"$12-$14/hr","type":"On-Campus Employment","details":"On-campus TA roles for %s courses are posted each term. Federal work-study eligible -- earnings do not affect next year's aid calculation.","url":"https://app.joinhandshake.com/"},
                      {"title":"Co-op / Practicum Placement at %s","pay":"$17-$22/hr","type":"Co-op / Internship","details":"Alternating-semester co-op arranged through the %s Career Center. Converts to full-time offers at a 60%% rate for participating employers.","url":"https://app.joinhandshake.com/"}
                    ]}""".formatted(major, major, major, college, college);

            case "counselor":
                return """
                    {"executiveSummary":"This student is pursuing %s at %s with a solid academic foundation. The current financial package leaves some unmet need that warrants a targeted appeal and external scholarship strategy. Early action on both fronts can meaningfully reduce the 4-year loan burden.","financialSnapshot":{"coa":"N/A","netPrice":"%s","unmetNeed":"N/A","loanBurden":"N/A","debtAtGrad":"N/A"},"strengthsForAppeal":["Strong academic record and demonstrated commitment to %s through coursework and activities","First-generation student status qualifies for additional institutional priority aid programs at %s","Extracurricular leadership demonstrates campus contribution potential valued by %s financial aid committee"],"parentTalkingPoints":["The current net price may be reduced by $2,000-$5,000 through a written appeal citing financial circumstances","External scholarships in %s can offset up to $3,000-$6,000 per year without affecting federal aid","Work-study earnings (up to $3,000/yr) do not count against next year's FAFSA EFC calculation","Unsubsidized loan interest begins accruing immediately; interest-only payments during school save hundreds at graduation"],"recommendedActions":["Submit a financial aid appeal letter to %s by the next review deadline with documentation","Apply for 3-5 major-specific scholarships before the February-March deadline window","Register for Handshake and apply to work-study or TA positions for Year 1","Schedule a follow-up meeting with the %s financial aid office 30 days after appeal submission"],"riskFlags":["Cumulative loan burden may exceed one year's median %s starting salary if aid package is not improved","Merit aid renewal requires maintaining minimum GPA; a single difficult semester can suspend awards at %s"]}""".formatted(major, college, req.getNetPrice() != null ? "$" + fmt(req.getNetPrice()) + "/yr" : "N/A", major, college, college, major, college, college, major, college);

            default:
                // Legacy full-blob stub for backward compat
                return """
                    {
                      "fieldSpecificScholarships": [
                        {"name": "Google Generation Scholarship", "amount": "$10,000", "sponsor": "Google", "eligibility": "CS or related field; underrepresented in tech; 3.0+ GPA", "deadline": "December annually", "url": "https://buildyourfuture.withgoogle.com/scholarships"},
                        {"name": "Microsoft Tuition Scholarship", "amount": "$5,000", "sponsor": "Microsoft", "eligibility": "Freshman-senior in CS or STEM; financial need", "deadline": "January annually", "url": "https://careers.microsoft.com/students/us/en/usscholarship"},
                        {"name": "AFCEA STEM Scholarship", "amount": "$2,500-$5,000", "sponsor": "AFCEA", "eligibility": "US citizen; STEM sophomore or above; 3.0+ GPA", "deadline": "February 28 annually", "url": "https://www.afcea.org/education/scholarships"}
                      ],
                      "employment": [
                        {"title": "Undergraduate Research Assistant -- %s Dept.", "pay": "Volunteer for Credit / $13-$15/hr", "type": "Undergraduate Research", "details": "Faculty in the %s program are actively recruiting research assistants each semester.", "url": "https://app.joinhandshake.com/"},
                        {"title": "Department Teaching Assistant / Tutor", "pay": "$12-$14/hr", "type": "On-Campus Employment", "details": "On-campus TA roles for %s students are posted each term.", "url": "https://app.joinhandshake.com/"},
                        {"title": "Co-op / Practicum Placement", "pay": "$17-$22/hr", "type": "Co-op / Internship", "details": "Alternating-semester co-op in %s-related firms near campus.", "url": "https://app.joinhandshake.com/"}
                      ],
                      "yearlyTips": [
                        "File FAFSA in October and submit a written aid appeal -- keep federal loans at or below $5,500 in Year 1.",
                        "Request a merit aid review if your GPA improved; explore department research stipends to offset Year 2 cost increases.",
                        "Apply for upperclassman scholarships (fewer applicants); use co-op income to reduce Parent PLUS dependency.",
                        "Maximize work-study and confirm senior-year GPA qualifies you for post-grad loan forgiveness programs."
                      ]
                    }
                    """.formatted(major, major, major, major);
        }
    }

    // ── Post-Grad Debt Management ─────────────────────────────────────────────

    public String getRepaymentRecommendation(com.example.collegeroitool.dto.DebtIntakeRequest req,
                                              java.util.List<java.util.Map<String, Object>> plans,
                                              java.util.Map<String, Object> pslfResult,
                                              String liveContent) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return "{\"recommendedPlan\":\"SAVE (formerly REPAYE)\",\"rationale\":\"Given your income relative to your loan balance, SAVE provides the lowest monthly payment and caps unpaid interest from growing your principal. This protects you if your income stays flat in the near term.\",\"keyInsight\":\"Your debt-to-income ratio is high — income-driven repayment is essential to avoid default.\",\"pslfNote\":null,\"warningFlag\":null}";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String plansJson = mapper.writeValueAsString(plans);
            Boolean pslfEligible = pslfResult != null ? (Boolean) pslfResult.get("pslfEligible") : null;
            String live = liveContent != null ? liveContent : "(No live content retrieved)";
            String prompt = repaymentPromptTemplate
                .replace("{{federalBalance}}", fmt(req.getFederalLoanBalance() != null ? req.getFederalLoanBalance() : 0))
                .replace("{{privateBalance}}", fmt(req.getPrivateLoanBalance() != null ? req.getPrivateLoanBalance() : 0))
                .replace("{{income}}", fmt(req.getAnnualGrossIncome() != null ? req.getAnnualGrossIncome() : 0))
                .replace("{{householdSize}}", String.valueOf(req.getHouseholdSize() != null ? req.getHouseholdSize() : 1))
                .replace("{{maritalStatus}}", req.getMaritalStatus() != null ? req.getMaritalStatus() : "not provided")
                .replace("{{employmentStatus}}", req.getEmploymentStatus() != null ? req.getEmploymentStatus() : "not provided")
                .replace("{{employerName}}", req.getEmployerName() != null ? req.getEmployerName() : "not provided")
                .replace("{{creditBand}}", req.getCreditScoreBand() != null ? req.getCreditScoreBand() : "not provided")
                .replace("{{servicer}}", req.getLoanServicer() != null ? req.getLoanServicer() : "not provided")
                .replace("{{pslfEligible}}", pslfEligible != null ? String.valueOf(pslfEligible) : "unknown")
                .replace("{{plansJson}}", plansJson)
                .replace("{{liveSearchContent}}", live);
            return callGroq(prompt, 700, 0.2);
        } catch (Exception e) {
            return "{\"error\":\"Could not generate recommendation: " + e.getMessage() + "\"}";
        }
    }

    /** @deprecated Use {@link #getRepaymentRecommendation(Object, java.util.List, java.util.Map, String)} */
    public String getRepaymentRecommendation(com.example.collegeroitool.dto.DebtIntakeRequest req,
                                              java.util.List<java.util.Map<String, Object>> plans,
                                              java.util.Map<String, Object> pslfResult) {
        return getRepaymentRecommendation(req, plans, pslfResult, null);
    }

    public String getHardshipLetter(com.example.collegeroitool.dto.DebtIntakeRequest req, String liveContent) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return buildHardshipLetterStub(req);
        }
        String live = liveContent != null ? liveContent : "(No live content retrieved)";
        String prompt = hardshipPromptTemplate
            .replace("{{servicer}}", req.getLoanServicer() != null ? req.getLoanServicer() : "Your Loan Servicer")
            .replace("{{federalBalance}}", fmt(req.getFederalLoanBalance() != null ? req.getFederalLoanBalance() : 0))
            .replace("{{income}}", fmt(req.getAnnualGrossIncome() != null ? req.getAnnualGrossIncome() : 0))
            .replace("{{householdSize}}", String.valueOf(req.getHouseholdSize() != null ? req.getHouseholdSize() : 1))
            .replace("{{employmentStatus}}", req.getEmploymentStatus() != null ? req.getEmploymentStatus() : "not provided")
            .replace("{{hardshipType}}", req.getHardshipType() != null ? req.getHardshipType() : "general")
            .replace("{{hardshipDetails}}", req.getHardshipDetails() != null ? req.getHardshipDetails() : "not provided")
            .replace("{{liveSearchContent}}", live);
        return callGroq(prompt, 1400, 0.3);
    }

    public String getHardshipLetter(com.example.collegeroitool.dto.DebtIntakeRequest req) {
        return getHardshipLetter(req, null);
    }

    public String getPrivateHardshipLetter(com.example.collegeroitool.dto.DebtIntakeRequest req, String liveContent) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return buildPrivateHardshipLetterStub(req);
        }
        String live = liveContent != null ? liveContent : "(No live content retrieved — visit lender website for current hardship policies)";
        String prompt = privateHardshipPromptTemplate
            .replace("{{privateLender}}", req.getPrivateLender() != null ? req.getPrivateLender() : "Your Private Lender")
            .replace("{{privateBalance}}", fmt(req.getPrivateLoanBalance() != null ? req.getPrivateLoanBalance() : 0))
            .replace("{{income}}", fmt(req.getAnnualGrossIncome() != null ? req.getAnnualGrossIncome() : 0))
            .replace("{{householdSize}}", String.valueOf(req.getHouseholdSize() != null ? req.getHouseholdSize() : 1))
            .replace("{{employmentStatus}}", req.getEmploymentStatus() != null ? req.getEmploymentStatus() : "not provided")
            .replace("{{hardshipType}}", req.getHardshipType() != null ? req.getHardshipType() : "financial hardship")
            .replace("{{hardshipDetails}}", req.getHardshipDetails() != null ? req.getHardshipDetails() : "not provided")
            .replace("{{liveSearchContent}}", live);
        return callGroq(prompt, 1500, 0.3);
    }

    private String buildPrivateHardshipLetterStub(com.example.collegeroitool.dto.DebtIntakeRequest req) {
        String lender   = req.getPrivateLender()      != null ? req.getPrivateLender()      : "Your Private Lender";
        String balance  = req.getPrivateLoanBalance() != null ? fmt(req.getPrivateLoanBalance()) : "N/A";
        String income   = req.getAnnualGrossIncome()  != null ? fmt(req.getAnnualGrossIncome())  : "N/A";
        String hardship = req.getHardshipType()       != null ? req.getHardshipType()       : "financial hardship";
        String details  = req.getHardshipDetails()    != null ? req.getHardshipDetails()    : "";
        int  household  = req.getHouseholdSize()      != null ? req.getHouseholdSize()      : 1;
        String employ   = req.getEmploymentStatus()   != null ? req.getEmploymentStatus()   : "not specified";

        return "[Date]\n\n"
            + "Hardship / Customer Assistance Department\n"
            + lender + "\n\n"
            + "Re: Request for Hardship Forbearance — Private Student Loan Account [ACCOUNT NUMBER]\n\n"
            + "To Whom It May Concern,\n\n"
            + "I am writing to formally request a hardship forbearance or payment reduction on my private student loan "
            + "with " + lender + " due to " + hardship + ". My current outstanding private loan balance is approximately $" + balance + ".\n\n"
            + "My household of " + household + " has an annual gross income of $" + income
            + ", and my current employment status is " + employ + "."
            + (details.isBlank() ? "" : " " + details) + "\n\n"
            + "I respectfully request a temporary forbearance or reduced payment arrangement for a period of 3–6 months "
            + "while I work to stabilize my financial situation. I am committed to resuming full payments as soon as "
            + "my circumstances allow and wish to avoid any negative impact on my credit or loan standing.\n\n"
            + "I am prepared to provide supporting documentation, including proof of income, employment status, "
            + "and household size, upon request. Please advise on any forms or documentation required by "
            + lender + " to process this hardship request.\n\n"
            + "Thank you for your time and consideration.\n\n"
            + "Sincerely,\n[BORROWER NAME]\n[ACCOUNT NUMBER]\n[Phone Number]\n[Email Address]\n\n"
            + "---CHECKLIST---\n"
            + "- Recent pay stubs or proof of income (last 30–60 days)\n"
            + "- Letter of unemployment or termination (if applicable)\n"
            + "- Bank statements showing current financial hardship\n"
            + "- Medical documentation (if hardship is health-related)\n"
            + "- Tax return or W-2 from most recent filing year\n"
            + "- Household size documentation (e.g., tax return or birth certificates)\n"
            + "- Any completed hardship or forbearance forms required by " + lender;
    }

    // ── FAFSA Prep ─────────────────────────────────────────────────────────────

    public String getFafsaReadinessSummary(com.example.collegeroitool.model.FafsaProfile profile) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return "{\"readinessSummary\":\"Your tax documentation looks complete for a standard FAFSA filing — wages and federal withholding are both present.\",\"aidProjection\":\"Based on the income shown, you're likely in range for partial need-based aid plus full federal loan eligibility. This is an estimate, not an award letter.\",\"appealOpportunities\":[{\"title\":\"Professional judgment review\",\"detail\":\"If your family's income has changed since this tax year, ask the financial aid office for a professional judgment review using current income instead.\"}],\"scholarshipQueries\":[\"first-generation college student scholarships 2026\",\"need-based scholarships for incoming freshmen 2026\"],\"deadlines\":[{\"name\":\"FAFSA opens\",\"timing\":\"October 1 each year\",\"note\":\"File as early as possible — some state and institutional aid is first-come, first-served.\"}]}";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String extractedDataJson = profile.getExtractedDataJson() != null ? profile.getExtractedDataJson() : "{}";
            String prompt = fafsaReadinessPromptTemplate
                .replace("{{studentName}}", profile.getStudentName() != null ? profile.getStudentName() : "not provided")
                .replace("{{dateOfBirth}}", profile.getDateOfBirth() != null ? profile.getDateOfBirth().toString() : "not provided")
                .replace("{{planningYear}}", profile.getPlanningYear() != null ? String.valueOf(profile.getPlanningYear()) : "not provided")
                .replace("{{extractedDataJson}}", extractedDataJson);
            return callGroq(prompt, 1200, 0.3);
        } catch (Exception e) {
            return "{\"error\":\"Could not generate readiness summary: " + e.getMessage() + "\"}";
        }
    }

    public String getFafsaRoadmap(com.example.collegeroitool.model.FafsaProfile profile,
                                   String deadlinesJson, String selectedOptionsJson) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return "{\"roadmapSteps\":[{\"order\":1,\"title\":\"File the FAFSA\",\"targetDate\":\"By October 1\",\"detail\":\"Submit as early as possible to maximize first-come, first-served state and institutional aid.\"},{\"order\":2,\"title\":\"Apply to selected scholarships\",\"targetDate\":\"Within 2 weeks of FAFSA filing\",\"detail\":\"Complete applications for the scholarships you selected while your financial documents are still organized.\"}],\"summary\":\"This plan front-loads your FAFSA filing, then layers in scholarship applications while your paperwork is fresh.\"}";
        }
        try {
            String prompt = fafsaRoadmapPromptTemplate
                .replace("{{studentName}}", profile.getStudentName() != null ? profile.getStudentName() : "not provided")
                .replace("{{planningYear}}", profile.getPlanningYear() != null ? String.valueOf(profile.getPlanningYear()) : "not provided")
                .replace("{{deadlinesJson}}", deadlinesJson != null ? deadlinesJson : "[]")
                .replace("{{selectedOptionsJson}}", selectedOptionsJson != null ? selectedOptionsJson : "[]");
            return callGroq(prompt, 900, 0.3);
        } catch (Exception e) {
            return "{\"error\":\"Could not generate roadmap: " + e.getMessage() + "\"}";
        }
    }

    public String getFafsaChatResponse(com.example.collegeroitool.model.FafsaProfile profile,
                                        String conversationHistory, String question) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return buildFafsaChatStub(profile, question);
        }
        String prompt = fafsaChatPromptTemplate
            .replace("{{studentName}}", profile.getStudentName() != null ? profile.getStudentName() : "not provided")
            .replace("{{dateOfBirth}}", profile.getDateOfBirth() != null ? profile.getDateOfBirth().toString() : "not provided")
            .replace("{{planningYear}}", profile.getPlanningYear() != null ? String.valueOf(profile.getPlanningYear()) : "not provided")
            .replace("{{extractedDataJson}}", profile.getExtractedDataJson() != null ? profile.getExtractedDataJson() : "{}")
            .replace("{{readinessSummaryJson}}", profile.getReadinessSummaryJson() != null ? profile.getReadinessSummaryJson() : "not generated yet")
            .replace("{{roadmapJson}}", profile.getRoadmapJson() != null ? profile.getRoadmapJson() : "not generated yet")
            .replace("{{conversationHistory}}", conversationHistory != null ? conversationHistory : "(no prior messages)")
            .replace("{{question}}", question);
        return callGroq(prompt, 600, 0.3);
    }

    public String getAssetRepositioningAdvice(com.example.collegeroitool.model.FafsaProfile profile) {
        return getAssetRepositioningAdvice(
            profile.getExtractedDataJson() != null ? profile.getExtractedDataJson() : "{}",
            null, null, null, null, null);
    }

    public String getAssetRepositioningAdvice(String kvJson, String awardYear, String handbookContent,
                                               Integer expectedTaxYear, Integer extractedTaxYear, String taxYearNote) {
        String year = awardYear != null ? awardYear : "2026-2027";
        if (DEV_STUB_KEY.equals(apiKey)) {
            String discrepancy = "null";
            if (expectedTaxYear != null && extractedTaxYear != null && !extractedTaxYear.equals(expectedTaxYear)) {
                discrepancy = "{\"extractedTaxYear\":" + extractedTaxYear + ",\"expectedTaxYear\":" + expectedTaxYear
                    + ",\"message\":\"Your uploaded documents are from " + extractedTaxYear
                    + ", but the " + year + " FAFSA requires " + expectedTaxYear
                    + " tax data. Please re-upload the correct year's tax documents.\""
                    + ",\"handbookRule\":\"FSA Handbook AVG Ch 2 (" + year + "): FAFSA uses Prior-Prior Year (PPY) tax data — always 2 years before the academic year start.\"}";
            }
            return "{\"discrepancy\":" + discrepancy + ",\"opportunities\":[{\"title\":\"Move $12,000 in taxable savings into a 401(k) before FAFSA filing\",\"fafsa_field\":\"Parent assets — net worth of investments (AVG Ch 3, " + year + ")\",\"rationale\":\"Taxable savings count toward the parent asset contribution rate of 12%; shifting $12,000 into a 401(k) removes it from the SAI calculation entirely.\"},{\"title\":\"Pay down $5,000 in credit card debt using liquid savings\",\"fafsa_field\":\"Parent assets — cash, savings and checking accounts (AVG Ch 3, " + year + ")\",\"rationale\":\"Consumer debt is not deducted from assets on FAFSA; reducing reportable cash by $5,000 directly lowers the net worth figure assessed at 12%.\"}],\"source\":\"FSA Handbook, AVG Ch 3 (" + year + ")\",\"source_url\":\"https://fsapartners.ed.gov/knowledge-center/fsa-handbook/" + year + "/application-and-verification-guide/ch3-student-aid-index-sai-and-pell-grant-eligibility\"}";
        }
        String handbook = handbookContent != null ? handbookContent : "(No live content retrieved — reason from training knowledge of FSA Handbook AVG Ch 3 " + year + ")";
        String prompt = fafsaAssetRepositioningPromptTemplate
            .replace("{{awardYear}}", year)
            .replace("{{expectedTaxYear}}", expectedTaxYear != null ? expectedTaxYear.toString() : "unknown")
            .replace("{{extractedTaxYear}}", extractedTaxYear != null ? extractedTaxYear.toString() : "not detected")
            .replace("{{taxYearNote}}", taxYearNote != null ? taxYearNote : "")
            .replace("{{handbookContent}}", handbook)
            .replace("{{extractedDataJson}}", kvJson != null ? kvJson : "{}");
        return callGroq(prompt, 1200, 0.3);
    }

    public String getProfessionalJudgmentAppeal(String studentName, String circumstancesJson,
                                                 String extractedDataJson, String awardYear, String handbookContent) {
        String year = awardYear != null ? awardYear : "2026-2027";
        if (DEV_STUB_KEY.equals(apiKey)) {
            return buildPjAppealStub(studentName, circumstancesJson, extractedDataJson, year);
        }
        String handbook = handbookContent != null ? handbookContent : "(No live content retrieved — reason from training knowledge of FSA Handbook Vol. 3, Ch. 5 " + year + ")";
        String prompt = fafsaPjAppealPromptTemplate
            .replace("{{awardYear}}", year)
            .replace("{{studentName}}", studentName != null ? studentName : "the student")
            .replace("{{circumstancesJson}}", circumstancesJson != null ? circumstancesJson : "[]")
            .replace("{{extractedDataJson}}", extractedDataJson != null ? extractedDataJson : "{}")
            .replace("{{handbookContent}}", handbook);
        return callGroq(prompt, 1600, 0.3);
    }

    @SuppressWarnings("unchecked")
    private String buildPjAppealStub(String studentName, String circumstancesJson,
                                      String extractedDataJson, String year) {
        String name = (studentName != null && !studentName.isBlank()) ? studentName : "the student";

        // Parse circumstances
        com.fasterxml.jackson.databind.ObjectMapper _om = new com.fasterxml.jackson.databind.ObjectMapper();
        List<String> bulletLines = new java.util.ArrayList<>();
        List<String> checklistLines = new java.util.ArrayList<>();
        try {
            List<java.util.Map<String, Object>> circs = _om.readValue(
                circumstancesJson != null ? circumstancesJson : "[]",
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
            for (java.util.Map<String, Object> c : circs) {
                String cat    = (String) c.getOrDefault("category", "");
                String detail = (String) c.getOrDefault("detail", "");
                String line = "• " + cat;
                if (detail != null && !detail.isBlank()) line += ": " + detail;
                bulletLines.add(line);
                checklistLines.add("• Documentation supporting \"" + cat + "\" (e.g. relevant records, statements, or official correspondence)");
            }
        } catch (Exception ignored) {}

        // Pull a few key financial figures
        String agi = "", wages = "", filingStatus = "", major = "";
        try {
            java.util.Map<String, Object> kv = _om.readValue(
                extractedDataJson != null ? extractedDataJson : "{}",
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
            agi         = String.valueOf(kv.getOrDefault("Adjusted Gross Income", ""));
            wages       = String.valueOf(kv.getOrDefault("Total Wages and Salaries", ""));
            filingStatus = String.valueOf(kv.getOrDefault("Filing Status", ""));
            major       = String.valueOf(kv.getOrDefault("Major", ""));
        } catch (Exception ignored) {}

        StringBuilder sb = new StringBuilder();
        sb.append("Dear Financial Aid Officer,\n\n");
        sb.append("I am writing to respectfully request a Professional Judgment (PJ) review of my financial aid package ")
          .append("for the ").append(year).append(" award year, pursuant to Section 479A of the Higher Education Act ")
          .append("and FSA Handbook Volume 3, Chapter 5 (").append(year).append(").\n\n");

        if (!agi.isBlank() || !major.isBlank()) {
            sb.append("My most recent tax return");
            if (!filingStatus.isBlank()) sb.append(" (").append(filingStatus).append(")");
            if (!agi.isBlank())   sb.append(" reflects an Adjusted Gross Income of $").append(agi);
            if (!wages.isBlank()) sb.append(", with total wages of $").append(wages);
            sb.append(".");
            if (!major.isBlank()) sb.append(" I am pursuing a degree in ").append(major).append(".");
            sb.append("\n\n");
        }

        if (!bulletLines.isEmpty()) {
            sb.append("However, my current financial situation has materially changed since that return was filed. ")
              .append("The following special circumstances warrant a re-evaluation of my demonstrated financial need:\n\n");
            bulletLines.forEach(l -> sb.append(l).append("\n"));
            sb.append("\n");
        }

        sb.append("I respectfully request that your office exercise Professional Judgment to adjust my Cost of Attendance ")
          .append("or Expected Family Contribution (SAI) as permitted under HEA § 479A to more accurately reflect my ")
          .append("current ability to pay. I am prepared to provide any supporting documentation you require.\n\n");
        sb.append("Thank you sincerely for your time and consideration.\n\n");
        sb.append("Respectfully,\n").append(name);

        // Checklist
        sb.append("\n---CHECKLIST---\n");
        sb.append("• Signed personal statement describing your special circumstance\n");
        checklistLines.forEach(l -> sb.append(l).append("\n"));
        sb.append("• Copy of most recent tax return (Form 1040) with all schedules\n");
        sb.append("• Contact information for the financial aid office PJ coordinator");

        return sb.toString();
    }

    public String getSaiStrategyCommentary(String projectionJson) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return "{\"summary\":\"Your projected SAI stays relatively stable across years based on the inputs provided, with small changes tracking your projected income and asset changes.\",\"strategyNotes\":[\"Consider timing large one-time income events (bonuses, capital gains) in years that matter less for aid, if possible.\"]}";
        }
        String prompt = fafsaSaiCommentaryPromptTemplate
            .replace("{{projectionJson}}", projectionJson != null ? projectionJson : "[]");
        return callGroq(prompt, 700, 0.3);
    }

    public String getCssFafsaExplainer(String extractedDataJson, String targetSchoolsJson) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return "{\"keyDifferences\":[{\"topic\":\"Home equity\",\"fafsaTreatment\":\"Not counted as an asset.\",\"cssTreatment\":\"Often counted, sometimes capped as a multiple of income.\",\"relevanceToStudent\":\"If your family owns a home with significant equity, CSS Profile schools may calculate a higher expected contribution than FAFSA-only schools.\"},{\"topic\":\"Non-custodial parent income\",\"fafsaTreatment\":\"Not required.\",\"cssTreatment\":\"Often required, even after divorce.\",\"relevanceToStudent\":\"If parents are divorced or separated, CSS Profile schools may still expect financial information from both parents.\"}],\"overallNote\":\"CSS Profile schools may calculate a meaningfully different (often higher) expected contribution than FAFSA-only schools, depending on home equity and family structure.\"}";
        }
        String prompt = fafsaCssExplainerPromptTemplate
            .replace("{{extractedDataJson}}", extractedDataJson != null ? extractedDataJson : "{}")
            .replace("{{targetSchoolsJson}}", targetSchoolsJson != null ? targetSchoolsJson : "[]");
        return callGroq(prompt, 900, 0.3);
    }

    // ── SCHOLARSHIP ──────────────────────────────────────────────────────────

    public String getScholarshipRecommendations(String studentContext, String searchResults) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return buildScholarshipStub(studentContext);
        }
        boolean hasSearchResults = searchResults != null && !searchResults.isBlank() && !searchResults.equals("[]");
        String searchSection = hasSearchResults
            ? "Live search results (JSON):\n" + searchResults + "\n\nFrom these results and your training knowledge, identify"
            : "No live search results available. Using your training knowledge, identify";
        String prompt = "You are a scholarship advisor. Return a single unified list covering both national/state/private external scholarships AND school-specific institutional awards — do not separate them.\n\n"
            + "Student profile:\n" + studentContext + "\n"
            + searchSection + " the best matching scholarships for this student.\n"
            + "Rules:\n"
            + "- Include a mix: national merit/demographic awards, state grants, major-specific awards, and (if target schools are listed) institutional awards from those schools.\n"
            + "- Every scholarship must be real and verifiable — real name, real sponsor, real URL where possible.\n"
            + "- Order by match quality (best fit first).\n"
            + "- Include 8-12 scholarships total.\n"
            + "Return ONLY a JSON array (no markdown, no explanation). Each element must have:\n"
            + "  name (string), amount (string or null), deadline (string or null),\n"
            + "  eligibility (1-2 sentence summary), link (URL or null), source (domain or \"groq-knowledge\"),\n"
            + "  type (one of: \"external\", \"school-specific\", \"state\").";
        return callGroq(prompt, 2000, 0.2);
    }

    /** @deprecated Use {@link #getScholarshipRecommendations(String, String)} */
    public String getScholarshipRecommendations(String studentContext, String searchResults, boolean schoolSpecific) {
        return getScholarshipRecommendations(studentContext, searchResults);
    }

    public String getScholarshipTimeline(String selectedScholarshipsJson, String studentName) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            String name = studentName != null ? studentName : "the student";
            return "[{\"week\":\"Now — Week 1\",\"action\":\"Gather all required materials: transcripts, letters of recommendation, essay drafts, and financial documents.\",\"relatedScholarship\":\"All\",\"dueDate\":null,\"category\":\"Prepare\"},"
                + "{\"week\":\"Week 2\",\"action\":\"Draft personal statement and scholarship essays. Have a counselor or teacher review your drafts.\",\"relatedScholarship\":\"All\",\"dueDate\":null,\"category\":\"Write\"},"
                + "{\"week\":\"Week 3\",\"action\":\"Request official transcripts and letters of recommendation from teachers/counselors.\",\"relatedScholarship\":\"All\",\"dueDate\":null,\"category\":\"Request\"},"
                + "{\"week\":\"Week 4\",\"action\":\"Submit scholarship applications. Double-check all requirements and confirm receipt.\",\"relatedScholarship\":\"All\",\"dueDate\":null,\"category\":\"Submit\"},"
                + "{\"week\":\"6–8 Weeks After Submission\",\"action\":\"Follow up with scholarship sponsors if no confirmation received. Check portal status.\",\"relatedScholarship\":\"All\",\"dueDate\":null,\"category\":\"Follow-up\"}]";
        }
        String prompt = "You are a college financial aid advisor. Generate a detailed action timeline for a student applying to these scholarships:\n\n"
            + selectedScholarshipsJson + "\n\n"
            + "Return ONLY a JSON array (no markdown). Each element represents one action item with:\n"
            + "  week (string, e.g. 'Week of June 23'), action (clear task description),\n"
            + "  relatedScholarship (scholarship name or 'All'), dueDate (ISO date or null),\n"
            + "  category (one of: Prepare, Write, Request, Submit, Follow-up, Award Notification).\n"
            + "Order chronologically. Include: gathering materials, writing essays, requesting transcripts/letters,\n"
            + "submission deadlines, and expected award announcement dates. Be specific with dates where available.";
        return callGroq(prompt, 1500, 0.3);
    }

    private String buildScholarshipStub(String studentContext) {
        // Parse key fields from the plain-text student context
        String ctx = studentContext != null ? studentContext.toLowerCase() : "";
        boolean firstGen   = ctx.contains("first generation") || ctx.contains("first-gen");
        boolean stem       = ctx.contains("engineer") || ctx.contains("computer science")
                          || ctx.contains("nursing") || ctx.contains("electrical");
        boolean lowIncome  = ctx.contains("18") || ctx.contains("pell") || ctx.contains("efc: 0")
                          || ctx.contains("efc: $0");
        boolean hispanic   = ctx.contains("ramirez") || ctx.contains("hispanic") || ctx.contains("latina");
        boolean asian      = ctx.contains("park") || ctx.contains("sharma") || ctx.contains("asian");

        java.util.List<String> scholarships = new java.util.ArrayList<>();
        scholarships.add("{\"name\":\"Federal Pell Grant\",\"amount\":\"Up to $7,395/year\",\"deadline\":\"FAFSA priority deadline varies by school\",\"eligibility\":\"Need-based federal grant for undergraduates with exceptional financial need. No repayment required.\",\"link\":\"https://studentaid.gov/understand-aid/types/grants/pell\",\"source\":\"studentaid.gov\",\"type\":\"external\"}");
        if (firstGen) {
            scholarships.add("{\"name\":\"Dell Scholars Program\",\"amount\":\"$20,000 total\",\"deadline\":\"December 1\",\"eligibility\":\"First-generation, low-income students with demonstrated need who are on track to graduate. Includes laptop and support resources.\",\"link\":\"https://www.dellscholars.org\",\"source\":\"dellscholars.org\",\"type\":\"external\"}");
            scholarships.add("{\"name\":\"Jack Kent Cooke Foundation College Scholarship\",\"amount\":\"Up to $55,000/year\",\"deadline\":\"November (high school seniors)\",\"eligibility\":\"High-achieving students with significant financial need, including first-generation students.\",\"link\":\"https://www.jkcf.org/our-scholarships/college-scholarship-program/\",\"source\":\"jkcf.org\",\"type\":\"external\"}");
        }
        if (stem) {
            scholarships.add("{\"name\":\"Gates Scholarship\",\"amount\":\"Full cost of attendance\",\"deadline\":\"September 15\",\"eligibility\":\"High-achieving, Pell-eligible minority students pursuing STEM or other high-need fields.\",\"link\":\"https://www.thegatesscholarship.org\",\"source\":\"thegatesscholarship.org\",\"type\":\"external\"}");
            scholarships.add("{\"name\":\"SMART Scholarship (DoD)\",\"amount\":\"Full tuition + stipend\",\"deadline\":\"December 1\",\"eligibility\":\"U.S. citizens pursuing STEM degrees who are willing to work for the Department of Defense after graduation.\",\"link\":\"https://www.smartscholarship.org\",\"source\":\"smartscholarship.org\",\"type\":\"external\"}");
        }
        if (hispanic) {
            scholarships.add("{\"name\":\"Hispanic Scholarship Fund\",\"amount\":\"$500–$5,000\",\"deadline\":\"February 15\",\"eligibility\":\"Hispanic-heritage students with minimum 3.0 GPA enrolled in a U.S. accredited college or university.\",\"link\":\"https://www.hsf.net\",\"source\":\"hsf.net\",\"type\":\"external\"}");
        }
        if (asian) {
            scholarships.add("{\"name\":\"Asian & Pacific Islander American Scholarship Fund\",\"amount\":\"$2,500–$20,000\",\"deadline\":\"January 10\",\"eligibility\":\"APIA heritage students with demonstrated financial need, minimum 2.7 GPA, and community involvement.\",\"link\":\"https://www.apiasf.org\",\"source\":\"apiasf.org\",\"type\":\"external\"}");
        }
        scholarships.add("{\"name\":\"Coca-Cola Scholars Program\",\"deadline\":\"October 31\",\"amount\":\"$20,000\",\"eligibility\":\"High school seniors demonstrating leadership in school and community activities, with strong academic achievement.\",\"link\":\"https://www.coca-colascholarsfoundation.org\",\"source\":\"coca-colascholarsfoundation.org\",\"type\":\"external\"}");
        scholarships.add("{\"name\":\"Elks National Foundation Most Valuable Student Scholarship\",\"amount\":\"$1,000–$12,500/year\",\"deadline\":\"November 15 (local lodge)\",\"eligibility\":\"U.S. citizens enrolled full-time as college freshmen, judged on scholarship, leadership, and financial need.\",\"link\":\"https://www.elks.org/scholars/scholarships/mvs.cfm\",\"source\":\"elks.org\",\"type\":\"external\"}");
        if (lowIncome) {
            scholarships.add("{\"name\":\"Questbridge National College Match\",\"amount\":\"Full four-year scholarship\",\"deadline\":\"September 27\",\"eligibility\":\"Low-income, high-achieving students with strong academics who may qualify for full scholarships at partner universities.\",\"link\":\"https://www.questbridge.org\",\"source\":\"questbridge.org\",\"type\":\"external\"}");
        }
        scholarships.add("{\"name\":\"Sallie Mae Bridging the Dream Scholarship\",\"amount\":\"$2,500\",\"deadline\":\"Multiple cycles per year\",\"eligibility\":\"Open to U.S. college students demonstrating financial need with a personal statement about their educational goals.\",\"link\":\"https://www.salliemae.com/college-planning/scholarships/\",\"source\":\"salliemae.com\",\"type\":\"external\"}");
        return "[" + String.join(",", scholarships) + "]";
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

    private String buildHardshipLetterStub(com.example.collegeroitool.dto.DebtIntakeRequest req) {
        String servicer  = req.getLoanServicer()      != null ? req.getLoanServicer()      : "Your Loan Servicer";
        String balance   = req.getFederalLoanBalance() != null ? fmt(req.getFederalLoanBalance()) : "N/A";
        String income    = req.getAnnualGrossIncome()  != null ? fmt(req.getAnnualGrossIncome())  : "N/A";
        String hardship  = req.getHardshipType()       != null ? req.getHardshipType()       : "financial hardship";
        String details   = req.getHardshipDetails()    != null ? req.getHardshipDetails()    : "";
        int    household = req.getHouseholdSize()      != null ? req.getHouseholdSize()      : 1;
        String employ    = req.getEmploymentStatus()   != null ? req.getEmploymentStatus()   : "not specified";

        return "Re: Request for Income-Driven Repayment Reconsideration — Federal Student Loan Account\n\n"
            + "To Whom It May Concern at " + servicer + ",\n\n"
            + "I am writing to formally request reconsideration of my federal student loan repayment plan due to "
            + hardship + ". My current outstanding federal loan balance is approximately $" + balance + ".\n\n"
            + "My household of " + household + " has an annual gross income of $" + income
            + ", and my current employment status is " + employ + "."
            + (details.isBlank() ? "" : " " + details) + "\n\n"
            + "Under 34 C.F.R. § 682.215 and the Income-Driven Repayment (IDR) provisions of the Higher Education Act, "
            + "borrowers experiencing financial hardship are entitled to have their monthly payments recalculated "
            + "based on current income and family size. I respectfully request that my account be reviewed under "
            + "the SAVE Plan (Saving on a Valuable Education) or an equivalent income-driven option.\n\n"
            + "I am prepared to submit Form IBR/SAVE along with supporting documentation, including proof of income "
            + "and household size, at your request.\n\n"
            + "Thank you for your prompt attention to this matter.\n\n"
            + "Sincerely,\n[Borrower Name]\n[Account Number]\n[Contact Information]";
    }

    private String buildFafsaChatStub(com.example.collegeroitool.model.FafsaProfile profile, String question) {
        String name = profile.getStudentName() != null ? profile.getStudentName() : "the student";
        String year = profile.getPlanningYear() != null ? String.valueOf(profile.getPlanningYear()) : "the upcoming";

        // Extract AGI from profile if available
        String agiNote = "";
        if (profile.getExtractedDataJson() != null && !profile.getExtractedDataJson().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> kv = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(profile.getExtractedDataJson(), java.util.Map.class);
                Object agi = kv.get("Adjusted Gross Income");
                if (agi != null) agiNote = " Based on the tax data on file (AGI: $" + agi + "), ";
            } catch (Exception ignored) {}
        }

        String q = question != null ? question.toLowerCase() : "";
        if (q.contains("pell") || q.contains("grant")) {
            return "For " + year + " award year:" + agiNote + "Pell Grant eligibility is primarily determined by your "
                + "Student Aid Index (SAI). Students with an SAI near zero qualify for the maximum Pell Grant "
                + "($7,395 for 2024-25). Eligibility phases out as SAI increases. File the FAFSA as early as October 1 "
                + "to secure priority consideration from your school's institutional aid office.";
        }
        if (q.contains("loan") || q.contains("borrow")) {
            return "Federal Direct Loans for " + year + " are available regardless of income." + agiNote
                + "Subsidized loans (need-based, no interest while enrolled) are preferable to Unsubsidized. "
                + "Independent students can borrow up to $9,500/year in Direct Loans (Year 1). "
                + "Always exhaust grants and scholarships before borrowing.";
        }
        if (q.contains("deadline") || q.contains("when")) {
            return "Key FAFSA deadlines for " + name + ": The federal deadline is June 30 of the award year, "
                + "but state and institutional deadlines are often much earlier — many fall between February and April. "
                + "File as soon as the FAFSA opens (October 1) to maximize first-come, first-served aid.";
        }
        return "Great question about " + name + "'s " + year + " financial aid." + agiNote
            + "I can help with FAFSA strategy, scholarship searches, professional judgment opportunities, "
            + "and aid appeal letters. To get a specific answer, I need a Groq API key configured — "
            + "please set GROQ_API_KEY in your environment. In the meantime, all other features "
            + "(readiness summary, roadmap, PJ appeal, scholarships) are fully functional.";
    }

    private String buildInstitutionalChatStub(String institutionName,
                                               List<Map<String, Object>> studentRoster, String question) {
        int count = studentRoster != null ? studentRoster.size() : 0;
        String q  = question != null ? question.toLowerCase() : "";

        // Try to answer about a specific student by name
        if (studentRoster != null) {
            for (Map<String, Object> s : studentRoster) {
                String sName = String.valueOf(s.getOrDefault("name", "")).toLowerCase();
                if (!sName.isBlank() && q.contains(sName.split(" ")[0].toLowerCase())) {
                    String financialData = String.valueOf(s.getOrDefault("financialData", "(no data)"));
                    return "Here is what I have on file for " + s.get("name") + " at " + institutionName + ":\n\n"
                        + financialData + "\n\nFor a deeper AI analysis, configure a GROQ_API_KEY.";
                }
            }
        }

        if (q.contains("how many") || q.contains("count") || q.contains("student")) {
            return institutionName + " currently has " + count + " student"
                + (count != 1 ? "s" : "") + " on file with Astra. "
                + "Ask me about a specific student by name to see their financial summary.";
        }
        if (q.contains("pell") || q.contains("grant")) {
            return "Across " + institutionName + "'s " + count + " enrolled students, Pell Grant eligibility "
                + "varies by SAI. Students with lower adjusted gross income (under ~$30k) are typically "
                + "eligible for substantial need-based aid. Review each student's profile for specific projections.";
        }
        return "I'm Astra, your financial aid assistant for " + institutionName + ". "
            + "I have " + count + " student" + (count != 1 ? "s" : "") + " on file. "
            + "Ask me about a specific student by name, or ask about aid trends, Pell eligibility, "
            + "or scholarship opportunities across your cohort.";
    }

    public String getInstitutionalChatResponse(String institutionName,
                                                List<Map<String, Object>> studentRoster,
                                                String conversationHistory,
                                                String question) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return buildInstitutionalChatStub(institutionName, studentRoster, question);
        }
        String rosterJson;
        try {
            rosterJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(studentRoster);
        } catch (Exception e) {
            rosterJson = studentRoster.toString();
        }
        String prompt = institutionalChatPromptTemplate
            .replace("{{institutionName}}", institutionName)
            .replace("{{studentCount}}", String.valueOf(studentRoster.size()))
            .replace("{{studentRosterJson}}", rosterJson)
            .replace("{{conversationHistory}}", conversationHistory != null ? conversationHistory : "(no prior messages)")
            .replace("{{question}}", question);
        return callGroq(prompt, 800, 0.3);
    }

    /**
     * General Astra chatbot for the undergrad-calculator branch.
     * history: list of {role, content} maps from prior turns.
     * liveContent: Tavily search results injected before calling Groq.
     */
    public String getAstraChatResponse(List<Map<String, Object>> history, String message, String liveContent) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return "I'm Astra! I can help with scholarships, financial aid award letters, and loan repayment. (Dev mode — Groq key not set)";
        }
        StringBuilder historyText = new StringBuilder();
        if (history != null) {
            for (Map<String, Object> turn : history) {
                String role    = String.valueOf(turn.getOrDefault("role", "user"));
                String content = String.valueOf(turn.getOrDefault("content", ""));
                historyText.append(role.equals("user") ? "Student: " : "Astra: ").append(content).append("\n");
            }
        }
        String live = (liveContent != null && !liveContent.isBlank()) ? liveContent
            : "(Live search unavailable — reason from training knowledge)";
        String prompt = astraChatPromptTemplate
            .replace("{{liveContent}}", live)
            .replace("{{conversationHistory}}", historyText.isEmpty() ? "(no prior messages)" : historyText.toString().trim())
            .replace("{{message}}", message);
        return callGroq(prompt, 700, 0.35);
    }

    public String summarizeStateAssistance(String state, String liveContent) {
        if (DEV_STUB_KEY.equals(apiKey)) {
            return "**State Assistance Programs (" + state + ")** — Dev mode placeholder. In production, Astra will summarize state-specific loan forgiveness, refinancing, and advocacy options from live search results.";
        }
        String prompt = """
            You are a financial aid advisor. A student lives in %s and needs to know about state-specific student loan relief options.

            The following live web content was retrieved from searches about %s student loan assistance:
            %s

            Based on the live content above, write a concise advisory summary (3-5 paragraphs, plain English) covering:
            1. State forgiveness or repayment assistance programs — who qualifies, how to apply, amounts available.
            2. State-chartered banks or credit unions offering student loan refinancing or balance transfer at competitive rates — be specific about institutions and rates if found.
            3. Nonprofit or legal advocacy organizations in %s that help student loan borrowers — name them, describe what they do, and how to contact them.

            Do not quote the sources verbatim. Synthesize the information as an advisor giving actionable guidance.
            If a category had no useful results, say so briefly and suggest the student search the state education agency website directly.
            Format with bold section headers for each of the 3 categories.
            """.formatted(state, state, liveContent, state);
        return callGroq(prompt, 900, 0.3);
    }

    private static String fmt(double value) {
        return String.format("%.0f", value);
    }
}
