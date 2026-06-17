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
    private String fafsaReadinessPromptTemplate;
    private String fafsaRoadmapPromptTemplate;
    private String fafsaChatPromptTemplate;
    private String fafsaAssetRepositioningPromptTemplate;
    private String fafsaPjAppealPromptTemplate;
    private String fafsaSaiCommentaryPromptTemplate;
    private String fafsaCssExplainerPromptTemplate;

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
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
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
                                              java.util.Map<String, Object> pslfResult) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return "{\"recommendedPlan\":\"SAVE (formerly REPAYE)\",\"rationale\":\"Given your income relative to your loan balance, SAVE provides the lowest monthly payment and caps unpaid interest from growing your principal. This protects you if your income stays flat in the near term.\",\"keyInsight\":\"Your debt-to-income ratio is high — income-driven repayment is essential to avoid default.\",\"pslfNote\":null,\"warningFlag\":null}";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String plansJson = mapper.writeValueAsString(plans);
            Boolean pslfEligible = pslfResult != null ? (Boolean) pslfResult.get("pslfEligible") : null;
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
                .replace("{{plansJson}}", plansJson);
            return callGroq(prompt, 600, 0.2);
        } catch (Exception e) {
            return "{\"error\":\"Could not generate recommendation: " + e.getMessage() + "\"}";
        }
    }

    public String getHardshipLetter(com.example.collegeroitool.dto.DebtIntakeRequest req) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return "[Demo mode] Hardship letter generation requires a live Groq API key. In production, Astra generates a complete, servicer-ready letter with your specific financial details and the applicable federal regulation citation.";
        }
        String prompt = hardshipPromptTemplate
            .replace("{{servicer}}", req.getLoanServicer() != null ? req.getLoanServicer() : "Your Loan Servicer")
            .replace("{{federalBalance}}", fmt(req.getFederalLoanBalance() != null ? req.getFederalLoanBalance() : 0))
            .replace("{{income}}", fmt(req.getAnnualGrossIncome() != null ? req.getAnnualGrossIncome() : 0))
            .replace("{{householdSize}}", String.valueOf(req.getHouseholdSize() != null ? req.getHouseholdSize() : 1))
            .replace("{{employmentStatus}}", req.getEmploymentStatus() != null ? req.getEmploymentStatus() : "not provided")
            .replace("{{hardshipType}}", req.getHardshipType() != null ? req.getHardshipType() : "general")
            .replace("{{hardshipDetails}}", req.getHardshipDetails() != null ? req.getHardshipDetails() : "not provided");
        return callGroq(prompt, 1200, 0.3);
    }

    // ── FAFSA Prep ─────────────────────────────────────────────────────────────

    public String getFafsaReadinessSummary(com.example.collegeroitool.model.FafsaProfile profile) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
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
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
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
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return "[Demo mode] Live chat requires a Groq API key. In production, Astra answers questions using only your saved FAFSA Prep profile.";
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
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return "{\"opportunities\":[{\"title\":\"Shift non-retirement savings into a retirement account\",\"rationale\":\"Retirement accounts (401k, IRA) are not counted as assets on the FAFSA, while regular savings and brokerage accounts are.\",\"caveat\":\"Confirm contribution limits and any early-withdrawal restrictions with a financial advisor before moving funds.\"}],\"generalNote\":\"Retirement accounts and primary home equity are excluded from FAFSA assets, but some CSS Profile schools do count home equity.\"}";
        }
        String prompt = fafsaAssetRepositioningPromptTemplate
            .replace("{{dependencyStatus}}", profile.getDependencyStatus() != null ? profile.getDependencyStatus() : "not yet determined")
            .replace("{{extractedDataJson}}", profile.getExtractedDataJson() != null ? profile.getExtractedDataJson() : "{}");
        return callGroq(prompt, 900, 0.3);
    }

    public String getProfessionalJudgmentAppeal(String studentName, String circumstancesJson) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return "[Demo mode] Professional judgment appeal letter generation requires a live Groq API key. In production, Astra drafts a complete letter referencing your specific selected circumstances.\n---CHECKLIST---\n- Documentation requires a live Groq API key to generate in demo mode.";
        }
        String prompt = fafsaPjAppealPromptTemplate
            .replace("{{studentName}}", studentName != null ? studentName : "the student")
            .replace("{{circumstancesJson}}", circumstancesJson != null ? circumstancesJson : "[]");
        return callGroq(prompt, 1200, 0.3);
    }

    public String getSaiStrategyCommentary(String projectionJson) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return "{\"summary\":\"Your projected SAI stays relatively stable across years based on the inputs provided, with small changes tracking your projected income and asset changes.\",\"strategyNotes\":[\"Consider timing large one-time income events (bonuses, capital gains) in years that matter less for aid, if possible.\"]}";
        }
        String prompt = fafsaSaiCommentaryPromptTemplate
            .replace("{{projectionJson}}", projectionJson != null ? projectionJson : "[]");
        return callGroq(prompt, 700, 0.3);
    }

    public String getCssFafsaExplainer(String extractedDataJson, String targetSchoolsJson) {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return "{\"keyDifferences\":[{\"topic\":\"Home equity\",\"fafsaTreatment\":\"Not counted as an asset.\",\"cssTreatment\":\"Often counted, sometimes capped as a multiple of income.\",\"relevanceToStudent\":\"If your family owns a home with significant equity, CSS Profile schools may calculate a higher expected contribution than FAFSA-only schools.\"},{\"topic\":\"Non-custodial parent income\",\"fafsaTreatment\":\"Not required.\",\"cssTreatment\":\"Often required, even after divorce.\",\"relevanceToStudent\":\"If parents are divorced or separated, CSS Profile schools may still expect financial information from both parents.\"}],\"overallNote\":\"CSS Profile schools may calculate a meaningfully different (often higher) expected contribution than FAFSA-only schools, depending on home equity and family structure.\"}";
        }
        String prompt = fafsaCssExplainerPromptTemplate
            .replace("{{extractedDataJson}}", extractedDataJson != null ? extractedDataJson : "{}")
            .replace("{{targetSchoolsJson}}", targetSchoolsJson != null ? targetSchoolsJson : "[]");
        return callGroq(prompt, 900, 0.3);
    }

    // ── SCHOLARSHIP ──────────────────────────────────────────────────────────

    public String getScholarshipRecommendations(String studentContext, String searchResults, boolean schoolSpecific) {
        String scope = schoolSpecific
            ? "school-specific institutional scholarships and grants from the student's target schools"
            : "external national, state, and private scholarships";
        boolean hasSearchResults = searchResults != null && !searchResults.isBlank() && !searchResults.equals("[]");
        String searchSection = hasSearchResults
            ? "Live search results (JSON):\n" + searchResults + "\n\nFrom these results, identify"
            : "No live search results are available. Using your training knowledge, identify";
        String prompt = "You are a scholarship advisor.\n\n"
            + "Student profile:\n" + studentContext + "\n"
            + searchSection + " the best matching " + scope + " for this student.\n"
            + "Return ONLY a JSON array (no markdown, no explanation). Each element must have:\n"
            + "  name (string), amount (string or null), deadline (string or null),\n"
            + "  eligibility (1-2 sentence summary), link (URL or null), source (domain or \"groq-knowledge\").\n"
            + "Include 5-10 scholarships the student is realistically eligible for. Order by match quality.";
        return callGroq(prompt, 1800, 0.2);
    }

    public String getScholarshipTimeline(String selectedScholarshipsJson, String studentName) {
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
