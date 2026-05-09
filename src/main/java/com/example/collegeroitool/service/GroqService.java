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

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", 3000);
        body.put("temperature", 0.7);

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

    private String buildDevStubAdvice(LlmAdviceRequest req) {
        String college = req.getCollegeName() != null ? req.getCollegeName() : "Sample University";
        String major   = req.getMajor()       != null ? req.getMajor()       : "Computer Science";

        return """
            {
              "schoolMajorResources": {
                "professionalSocieties": [
                  {"name": "[DEV] Association for Computing Machinery (ACM) — %s Chapter", "description": "Student chapter hosting weekly coding competitions, guest speakers from industry, and annual hackathon. Open to all CS and related majors. Meetings Thursdays 6pm in Engineering Hall."},
                  {"name": "[DEV] Institute of Electrical and Electronics Engineers (IEEE) — %s Student Branch", "description": "Hands-on project teams in robotics, embedded systems, and circuit design. Connects members with internship pipelines at Boeing, Intel, and regional tech firms."},
                  {"name": "[DEV] National Society of Black Engineers (NSBE) — %s Chapter", "description": "Academic excellence and professional development for underrepresented engineering and tech students. Hosts resume workshops and the annual NSBE Regional Conference networking fair."}
                ],
                "freshmanResources": [
                  {"name": "[DEV] First-Year Experience (FYE) Office — %s", "description": "Dedicated staff helping new students navigate registration, campus life transitions, and academic goal-setting. Drop-in hours Monday–Friday 9am–4pm in the Student Success Center, Room 110."},
                  {"name": "[DEV] Financial Aid Appeals & Special Circumstances — %s Office of Financial Aid", "description": "If your family's financial situation has changed since filing the FAFSA, submit a Special Circumstances appeal form. The average additional award for successful appeals is $2,000–$4,000. Visit finaid.%s.edu or call the aid office directly."},
                  {"name": "[DEV] Early Alert Academic Support System — %s", "description": "Faculty submit early alerts for students showing early signs of academic struggle. The system connects you with peer tutoring, supplemental instruction, and academic coaching before midterms."},
                  {"name": "[DEV] Student Emergency Fund — %s", "description": "One-time grants up to $500 for students experiencing unexpected financial hardship (car repair, medical co-pay, textbook emergency). Apply online through the Dean of Students portal; decisions within 48 hours."},
                  {"name": "[DEV] Honors College & Scholarship Advising — %s", "description": "Even if not in the Honors program, all freshmen can meet with scholarship advisors for Goldwater, Fulbright, and national fellowship guidance. Office hours by appointment in the Honors House."}
                ]
              },
              "privateScholarships": {
                "national": [
                  {"name": "Gates Scholarship", "amount": "Full Cost of Attendance", "sponsor": "Bill & Melinda Gates Foundation", "eligibility": "Minority students with Pell Grant eligibility, GPA 3.3+, demonstrated leadership", "deadline": "September 15 annually", "url": "https://www.thegatesscholarship.org/scholarship"},
                  {"name": "Dell Scholars Program", "amount": "$20,000 + laptop + resources", "sponsor": "Michael & Susan Dell Foundation", "eligibility": "Pell Grant eligible, 2.4+ GPA, first-generation or low-income, strong perseverance story", "deadline": "December 1 annually", "url": "https://www.dellscholars.org/scholarship/"},
                  {"name": "Jack Kent Cooke Foundation Scholarship", "amount": "Up to $55,000/year", "sponsor": "Jack Kent Cooke Foundation", "eligibility": "High-achieving community college transfer or undergraduate students with financial need", "deadline": "January annually", "url": "https://www.jkcf.org/our-scholarships/college-scholarship-program/"},
                  {"name": "QuestBridge National College Match", "amount": "Full 4-year scholarship", "sponsor": "QuestBridge", "eligibility": "High-achieving, low-income high school seniors; family income under $65,000", "deadline": "September annually", "url": "https://www.questbridge.org/"},
                  {"name": "Coca-Cola Scholars Program", "amount": "$20,000", "sponsor": "Coca-Cola Scholars Foundation", "eligibility": "High school seniors with leadership, service, and academic excellence; US citizen or permanent resident", "deadline": "October 31 annually", "url": "https://www.coca-colascholarsfoundation.org/"},
                  {"name": "Horatio Alger Association Scholarship", "amount": "$25,000", "sponsor": "Horatio Alger Association", "eligibility": "Students who have faced significant adversity; financial need; 2.0+ GPA; US citizen", "deadline": "October 25 annually", "url": "https://scholars.horatioalger.org/"}
                ],
                "fieldSpecific": [
                  {"name": "Google Generation Scholarship", "amount": "$10,000", "sponsor": "Google", "eligibility": "Computer science or related field students who are underrepresented in tech; 3.0+ GPA", "deadline": "December annually", "url": "https://buildyourfuture.withgoogle.com/scholarships/google-scholarship-recipients"},
                  {"name": "Microsoft Tuition Scholarship", "amount": "$5,000", "sponsor": "Microsoft", "eligibility": "Freshman–senior studying CS, STEM, or related; financial need; underrepresented background encouraged to apply", "deadline": "January annually", "url": "https://careers.microsoft.com/students/us/en/usscholarship"},
                  {"name": "AFCEA STEM Scholarship", "amount": "$2,500–$5,000", "sponsor": "Armed Forces Communications and Electronics Association", "eligibility": "US citizen studying STEM; sophomore or above; 3.0+ GPA", "deadline": "February 28 annually", "url": "https://www.afcea.org/education/scholarships"},
                  {"name": "Society of Women Engineers (SWE) Scholarship", "amount": "$1,000–$15,000", "sponsor": "Society of Women Engineers", "eligibility": "Women or nonbinary students in engineering or computer science programs", "deadline": "February 15 annually", "url": "https://swe.org/scholarships/"},
                  {"name": "National GEM Consortium Fellowship", "amount": "Full tuition + stipend", "sponsor": "GEM Consortium", "eligibility": "Underrepresented minorities pursuing STEM graduate degrees; also supports undergrad pipeline programs", "deadline": "November 15 annually", "url": "https://www.gemfellowship.org/"}
                ],
                "communityIdentity": [
                  {"name": "Hispanic Scholarship Fund (HSF)", "amount": "$500–$5,000", "sponsor": "Hispanic Scholarship Fund", "eligibility": "Hispanic/Latino heritage; US citizen or permanent resident; 3.0+ GPA; financial need", "deadline": "February 15 annually", "url": "https://www.hsf.net/scholarship"},
                  {"name": "United Negro College Fund (UNCF) Scholarships", "amount": "Varies ($2,000–$10,000+)", "sponsor": "UNCF", "eligibility": "African American students; financial need; GPA requirements vary by program", "deadline": "Rolling / varies by program", "url": "https://uncf.org/scholarships"},
                  {"name": "Point Foundation Scholarship", "amount": "Up to full cost of attendance", "sponsor": "Point Foundation", "eligibility": "LGBTQ+ students with strong academic record, leadership, and financial need", "deadline": "January annually", "url": "https://pointfoundation.org/point-apply/apply-now/"},
                  {"name": "First-Generation Foundation Scholarship", "amount": "$1,000–$3,500", "sponsor": "First-Generation Foundation", "eligibility": "First-generation college students with demonstrated financial need and academic promise", "deadline": "March 1 annually", "url": "https://www.firstgenerationfoundation.org/scholarships"},
                  {"name": "Knights of Columbus Pro Deo & Pro Patria Scholarship", "amount": "$1,500/year", "sponsor": "Knights of Columbus", "eligibility": "Catholic students attending Catholic colleges; financial need; US or Canadian citizen", "deadline": "March 1 annually", "url": "https://www.kofc.org/en/what-we-do/scholarships.html"}
                ]
              },
              "keyConsiderations": [
                {"title": "[DEV] Federal Loan Sustainability vs. Income Threshold", "body": "Borrowing $5,500/yr in federal loans over 4 years = $22,000 total. At a 6.5%% rate on a 10-year plan, your monthly payment would be approximately $249/month ($2,988/year). This is roughly 5-6%% of a $55,000 median starting salary — within the sustainable range. Aim to keep total debt at graduation at or below one year's projected starting salary."},
                {"title": "[DEV] Unsubsidized Loan Interest Accrual", "body": "Unlike subsidized loans, unsubsidized loan interest begins accruing the moment funds are disbursed. On $3,500 unsubsidized at 6.5%% over 4 years of enrollment, you will owe approximately $910 in capitalized interest before your first payment is due — increasing your effective loan balance at graduation."},
                {"title": "[DEV] Unmet Need Gap Strategy — Address the $8,000/yr Shortfall", "body": "With an estimated $8,000/year unmet need, your 4-year gap is approximately $32,000. Options to bridge this: (1) submit a written financial aid appeal citing any change in family income; (2) apply for 3–5 external scholarships before your sophomore year; (3) consider 10–15 hrs/week of part-time work, which at $14/hr yields ~$7,000/yr — nearly closing the gap without additional loans."},
                {"title": "[DEV] Merit Aid GPA Maintenance Requirement", "body": "Most institutional grants and merit scholarships require maintaining a minimum cumulative GPA (typically 3.0–3.25). A single semester of academic difficulty can trigger a suspension of your institutional award, adding thousands to your out-of-pocket cost. Contact the financial aid office in writing if your GPA drops before they send a renewal notice — early communication often allows a one-semester grace period."},
                {"title": "[DEV] Family Contribution & Parent PLUS Loan Consideration", "body": "The Family Contribution field includes any Parent PLUS loan your family is considering. PLUS loans carry a 9.08%% interest rate (vs. 6.5%% for federal student loans) and begin repayment immediately upon disbursement unless a deferment is requested. If PLUS borrowing is necessary, request income-contingent deferment and prioritize paying it down before it reaches year 4 compounding."}
              ]
            }
            """.formatted(college, college, college, college, college, college.toLowerCase().replace(" ", ""), college, college, college);
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
        double federalLoan = (req.getSubsidizedLoan()   != null ? req.getSubsidizedLoan()   : 0)
                           + (req.getUnsubsidizedLoan() != null ? req.getUnsubsidizedLoan() : 0);
        double parentPlus  =  req.getParentPlusLoan()   != null ? req.getParentPlusLoan()   : 0;
        double pellGrant   =  req.getPellGrant()         != null ? req.getPellGrant()         : 0;
        double instGrant   =  req.getInstitutionalGrant()!= null ? req.getInstitutionalGrant(): 0;
        double scholarship =  req.getScholarshipAmount() != null ? req.getScholarshipAmount() : 0;
        double workStudy   =  req.getWorkStudy()         != null ? req.getWorkStudy()         : 0;

        double netPrice  = req.getComputedNetPrice()  != null ? req.getComputedNetPrice()
                         : (req.getNetPrice()         != null ? req.getNetPrice()  : 0);
        double unmetNeed = req.getComputedUnmetNeed() != null ? req.getComputedUnmetNeed() : 0;
        double coa       = req.getComputedCOA()       != null ? req.getComputedCOA()       : 0;
        double majorEarnings     = req.getSixYrEarnings()         != null ? req.getSixYrEarnings()         : 0;
        double collegeWideEarnings = req.getCollegeWideEarnings() != null ? req.getCollegeWideEarnings()   : majorEarnings;

        String collegeName = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major       = req.getMajor()       != null ? req.getMajor()       : "Undecided";
        String residency   = req.getResidency()   != null ? req.getResidency()   : "instate";
        String living      = req.getLivingSituation() != null ? req.getLivingSituation() : "oncampus";

        return String.format("""
            You are a college financial aid expert producing a structured report for a student and their family. Return ONLY valid JSON — absolutely no markdown, no explanation, no code fences — in exactly this structure:

            {
              "schoolMajorResources": {
                "professionalSocieties": [{"name":"...","description":"..."}, {"name":"...","description":"..."}, {"name":"...","description":"..."}],
                "freshmanResources": [{"name":"...","description":"..."}, {"name":"...","description":"..."}, {"name":"...","description":"..."}, {"name":"...","description":"..."}, {"name":"...","description":"..."}]
              },
              "privateScholarships": {
                "national": [{"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}],
                "fieldSpecific": [{"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}],
                "communityIdentity": [{"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}, {"name":"...","amount":"...","sponsor":"...","eligibility":"...","deadline":"...","url":"..."}]
              },
              "keyConsiderations": [{"title":"...","body":"..."}, {"title":"...","body":"..."}, {"title":"...","body":"..."}, {"title":"...","body":"..."}, {"title":"...","body":"..."}]
            }

            STUDENT CONTEXT (use these exact numbers in your response):
            - College: %s
            - Major: %s
            - Residency: %s
            - Housing: %s
            - Computed COA: $%.0f/yr
            - Net Price (after free aid): $%.0f/yr
            - Unmet Need: $%.0f/yr
            - Federal Loans (subsidized + unsubsidized): $%.0f/yr
            - Parent PLUS / Family Contribution: $%.0f/yr
            - Pell Grant: $%.0f | Institutional Grant: $%.0f | Scholarships: $%.0f | Work-Study: $%.0f
            - Major-Specific Median Earnings (6yr): $%.0f/yr
            - College-Wide Median Earnings (6yr): $%.0f/yr

            RULES:
            schoolMajorResources: specific to %s and %s.
              professionalSocieties: exactly 3 real professional organization chapters or student clubs at %s relevant to %s (include chapter name, meeting info if known).
              freshmanResources: exactly 5 items covering in order: (1) First-Year Experience office specific to %s, (2) Financial Aid Appeals/Special Circumstances process at %s, (3) Early Alert Academic Support system at %s, (4) Student Emergency Fund at %s, (5) Honors College or Scholarship Advising at %s. Each must be specific to the actual institution.

            privateScholarships: use your most current knowledge, NOT personalized to this student.
              national: exactly 6 — use Gates Scholarship, Dell Scholars Program, Jack Kent Cooke Foundation, QuestBridge, Coca-Cola Scholars, Horatio Alger Association. Real amounts, deadlines, URLs.
              fieldSpecific: exactly 5 real scholarships relevant to %s field/major. Real organizations, real URLs.
              communityIdentity: exactly 5 real identity/community/faith scholarships. Real organizations, real URLs.

            keyConsiderations: exactly 5 items referencing the actual dollar figures above. Topics must be:
              1. Federal loan scenario vs income threshold (reference $%.0f federal loans × 4 years, monthly payment calculation at 6.5%% 10-yr)
              2. Unsubsidized loan interest accrual during enrollment
              3. Unmet need gap strategy (reference the $%.0f/yr gap specifically)
              4. Merit aid GPA maintenance requirement
              5. Parent PLUS loan situation (reference $%.0f/yr if non-zero, or advise avoiding PLUS if zero)

            All dollar figures must match the student context exactly. Be specific, actionable, and counselor-ready.
            """,
            collegeName, major, residency, living,
            coa, netPrice, unmetNeed,
            federalLoan, parentPlus,
            pellGrant, instGrant, scholarship, workStudy,
            majorEarnings, collegeWideEarnings,
            collegeName, major, collegeName, major,
            collegeName, collegeName, collegeName, collegeName, collegeName,
            major,
            federalLoan, unmetNeed, parentPlus
        );
    }
}
