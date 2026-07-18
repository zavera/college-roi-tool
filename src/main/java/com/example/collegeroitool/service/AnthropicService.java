package com.example.collegeroitool.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.example.collegeroitool.dto.DebtIntakeRequest;
import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.model.FafsaHandbookReference;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.repository.FafsaHandbookReferenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Claude-backed analysis for FAFSA Prep's asset-repositioning tab. Uses hardcoded FSA
 *  Handbook / studentaid.gov excerpts from {@link FafsaHandbookReferenceRepository} instead
 *  of a live web search — cheaper in tokens, at the cost of needing manual review whenever
 *  the FSA Handbook changes (see FafsaHandbookReferenceSeeder).
 *
 *  Every public method takes a trailing (userId, sessionId) pair: {@link TokenUsageService}
 *  checks the caller's monthly spend cap before the API call and records actual token usage
 *  after it. Pass null for both when there's no authenticated user (dev bypass without a real
 *  user, etc.) — usage tracking becomes a no-op in that case, same as this codebase's existing
 *  "skip persistence if user is null" pattern elsewhere. */
@Service
public class AnthropicService {

    private static final String DEV_STUB_KEY = "ANTHROPIC_API_KEY_NOT_SET";
    public static final String ASSET_REPOSITIONING_PROMPT_FILE = "fafsa-asset-repositioning-claude-prompt.txt";
    public static final String SCHOLARSHIP_RECOMMENDATIONS_PROMPT_FILE = "scholarship-recommendations-claude-prompt.txt";
    public static final String AWARD_ASSIST_PROMPT_FILE = "award-assist-claude-prompt.txt";
    public static final String POSTGRAD_REPAYMENT_PROMPT_FILE = "postgrad-repayment-recommendation-claude-prompt.txt";
    public static final String POSTGRAD_STATE_ASSISTANCE_PROMPT_FILE = "postgrad-state-assistance-claude-prompt.txt";
    public static final String POSTGRAD_SEARCH_SUMMARY_PROMPT_FILE = "postgrad-search-summary-claude-prompt.txt";

    /** Trusted scholarship-site domains — restricts both the web_search tool (here) and
     *  {@code ScholarshipService.validateLinks}'s link verification to the same allowlist. */
    public static final List<String> SCHOLARSHIP_ALLOWED_DOMAINS = List.of(
        "fastweb.com", "scholarships.com", "scholarships360.org", "bold.org",
        "niche.com", "cappex.com", "goingmerry.com", "collegescholarships.org",
        "studentaid.gov", "unigo.com", "collegexpress.com", "petersons.com",
        "collegeboard.org", "salliemae.com"
    );

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.model:claude-opus-4-8}")
    private String model;

    @Value("${anthropic.model.scholarship:claude-sonnet-5}")
    private String scholarshipModel;

    @Value("${anthropic.model.awardassist:claude-haiku-4-5}")
    private String awardAssistModel;

    @Value("${anthropic.model.postgrad:claude-haiku-4-5}")
    private String postgradModel;

    @Value("${anthropic.model.chatbot:claude-haiku-4-5}")
    private String chatbotModel;

    private static final String CHATBOT_TOOL_NAME = "get_my_saved_data_history";
    private static final int CHATBOT_MAX_TOOL_ITERATIONS = 5;

    private final FafsaHandbookReferenceRepository handbookReferenceRepository;
    private final TokenUsageService tokenUsageService;
    private final ChatbotContextService chatbotContextService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AnthropicClient client;
    private String fafsaAssetRepositioningPromptTemplate;
    private String scholarshipRecommendationsPromptTemplate;
    private String awardAssistPromptTemplate;
    private String postgradRepaymentPromptTemplate;
    private String postgradStateAssistancePromptTemplate;
    private String postgradSearchSummaryPromptTemplate;

    public AnthropicService(FafsaHandbookReferenceRepository handbookReferenceRepository,
                             TokenUsageService tokenUsageService,
                             ChatbotContextService chatbotContextService) {
        this.handbookReferenceRepository = handbookReferenceRepository;
        this.tokenUsageService = tokenUsageService;
        this.chatbotContextService = chatbotContextService;
    }

    @PostConstruct
    private void init() throws Exception {
        if (!DEV_STUB_KEY.equals(apiKey)) {
            client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        }
        fafsaAssetRepositioningPromptTemplate = new String(
            new ClassPathResource("prompts/" + ASSET_REPOSITIONING_PROMPT_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        scholarshipRecommendationsPromptTemplate = new String(
            new ClassPathResource("prompts/" + SCHOLARSHIP_RECOMMENDATIONS_PROMPT_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        awardAssistPromptTemplate = new String(
            new ClassPathResource("prompts/" + AWARD_ASSIST_PROMPT_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        postgradRepaymentPromptTemplate = new String(
            new ClassPathResource("prompts/" + POSTGRAD_REPAYMENT_PROMPT_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        postgradStateAssistancePromptTemplate = new String(
            new ClassPathResource("prompts/" + POSTGRAD_STATE_ASSISTANCE_PROMPT_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        postgradSearchSummaryPromptTemplate = new String(
            new ClassPathResource("prompts/" + POSTGRAD_SEARCH_SUMMARY_PROMPT_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }

    public String getModel() {
        return model;
    }

    public String getScholarshipModel() {
        return scholarshipModel;
    }

    public String getAwardAssistModel() {
        return awardAssistModel;
    }

    public String getPostgradModel() {
        return postgradModel;
    }

    public String getChatbotModel() {
        return chatbotModel;
    }

    /** Runs the FAFSA Prep asset-repositioning analysis using hardcoded FSA Handbook /
     *  studentaid.gov excerpts for the given award year instead of a live web search. */
    public String getAssetRepositioningAdvice(String kvJson, String awardYear,
                                               Integer expectedTaxYear, Integer extractedTaxYear, String taxYearNote,
                                               Long userId, String sessionId) {
        String year = awardYear != null ? awardYear : "2026-2027";
        if (client == null) {
            String discrepancy = "null";
            if (expectedTaxYear != null && extractedTaxYear != null && !extractedTaxYear.equals(expectedTaxYear)) {
                discrepancy = "{\"extractedTaxYear\":" + extractedTaxYear + ",\"expectedTaxYear\":" + expectedTaxYear
                    + ",\"message\":\"Your uploaded documents are from " + extractedTaxYear
                    + ", but the " + year + " FAFSA requires " + expectedTaxYear
                    + " tax data. Please re-upload the correct year's tax documents.\""
                    + ",\"handbookRule\":\"FSA Handbook AVG Ch 2 (" + year + "): FAFSA uses Prior-Prior Year (PPY) tax data, always 2 years before the academic year start.\"}";
            }
            return "{\"discrepancy\":" + discrepancy + ",\"opportunities\":[{\"title\":\"Move $12,000 in taxable savings into a 401(k) before FAFSA filing\",\"fafsa_field\":\"Parent assets, net worth of investments (AVG Ch 3, " + year + ")\",\"rationale\":\"Taxable savings count toward the parent asset contribution rate of 12%; shifting $12,000 into a 401(k) removes it from the SAI calculation entirely.\"}],\"source\":\"FSA Handbook, AVG Ch 3 (" + year + ")\",\"source_url\":\"https://fsapartners.ed.gov/knowledge-center/fsa-handbook/" + year + "/application-and-verification-guide/ch3-student-aid-index-sai-and-pell-grant-eligibility\"}";
        }

        tokenUsageService.checkCapOrThrow(userId);

        String prompt = fafsaAssetRepositioningPromptTemplate
            .replace("{{awardYear}}", year)
            .replace("{{expectedTaxYear}}", expectedTaxYear != null ? expectedTaxYear.toString() : "unknown")
            .replace("{{extractedTaxYear}}", extractedTaxYear != null ? extractedTaxYear.toString() : "not detected")
            .replace("{{taxYearNote}}", taxYearNote != null ? taxYearNote : "")
            .replace("{{handbookContent}}", buildHandbookContent(year))
            .replace("{{extractedDataJson}}", kvJson != null ? kvJson : "{}");

        MessageCreateParams params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4096L)
            .addUserMessage(prompt)
            .build();

        Message response = client.messages().create(params);
        recordUsage(response, userId, sessionId, InputPayloadType.FAFSA, model);
        return extractLastText(response);
    }

    /** Formats/ranks already-fetched live scholarship search results (from Tavily, via
     *  ScholarshipService) into the scholarship list shown to the user. Tried having Claude's
     *  own web_search tool do the searching directly (medium effort, no pre-fetch) — it worked,
     *  but burned far more tokens/time per search (multi-round tool loop) than this pattern.
     *  Reverted 2026-07-18. This call is "rendering" of results already in hand, not research —
     *  runs on Haiku, the cheapest tier, with no {@code output_config.effort} set: that
     *  parameter isn't supported on Haiku 4.5 (the API rejects it with a 400). */
    public String getScholarshipRecommendations(String studentContext, String searchResultsJson,
                                                 Long userId, String sessionId) {
        if (client == null) {
            return "[{\"name\":\"Coca-Cola Scholars Program\",\"amount\":\"$20,000\",\"deadline\":\"October (annual)\","
                + "\"eligibility\":\"High school seniors with strong academics and community involvement.\","
                + "\"link\":\"https://www.coca-colascholarsfoundation.org/\",\"source\":\"claude-knowledge\",\"type\":\"external\"}]";
        }

        tokenUsageService.checkCapOrThrow(userId);

        boolean hasSearchResults = searchResultsJson != null && !searchResultsJson.isBlank()
            && !searchResultsJson.equals("[]");
        String searchSection = hasSearchResults
            ? "Live search results (JSON):\n" + searchResultsJson + "\n\nFrom these results and your training knowledge, identify"
            : "No live search results available. Using your training knowledge, identify";

        String prompt = scholarshipRecommendationsPromptTemplate
            .replace("{{studentContext}}", studentContext != null ? studentContext : "")
            .replace("{{searchSection}}", searchSection);

        MessageCreateParams params = MessageCreateParams.builder()
            .model(scholarshipModel)
            .maxTokens(4096L)
            .addUserMessage(prompt)
            .build();

        Message response = client.messages().create(params);
        recordUsage(response, userId, sessionId, InputPayloadType.SCHOLARSHIP, scholarshipModel);
        return extractLastText(response);
    }

    /** Generates Award Assist's "AI Financial Summary" content — school/major resources,
     *  scholarships, employment opportunities, and key considerations — grounded in live
     *  search results (from Tavily, via AwardAssistService). The Financial Gap Analysis,
     *  Student Profile, Key Metrics, and repayment-scenario tables shown alongside this in the
     *  UI are all deterministic client-side math (app.html) — this call has no part in them,
     *  and it must never be asked to recompute or restate those figures as if it derived them. */
    public String getFinancialAdvice(LlmAdviceRequest req, String liveSearchContent, Long userId, String sessionId) {
        String collegeName = req.getCollegeName() != null ? req.getCollegeName() : "this college";
        String major       = req.getMajor()       != null ? req.getMajor()       : "Undecided";

        if (client == null) {
            return "{\"schoolMajorResources\":{\"professionalSocieties\":[{\"name\":\"Engineering Student Council\",\"description\":\"General student organization; dev-mode placeholder data.\"}],\"freshmanResources\":[]},\"scholarships\":[],\"employmentOpportunities\":[{\"title\":\"Undergraduate Research Assistant\",\"type\":\"Research\",\"pay\":\"$15/hr\",\"details\":\"Dev-mode placeholder data.\"}],\"keyConsiderations\":[{\"title\":\"Federal loan sustainability\",\"details\":[\"Dev-mode placeholder data.\"]}]}";
        }

        tokenUsageService.checkCapOrThrow(userId);

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
        double majorEarnings       = req.getSixYrEarnings()       != null ? req.getSixYrEarnings()       : 0;
        double collegeWideEarnings = req.getCollegeWideEarnings() != null ? req.getCollegeWideEarnings() : majorEarnings;

        String residency = req.getResidency()       != null ? req.getResidency()       : "instate";
        String living    = req.getLivingSituation() != null ? req.getLivingSituation() : "oncampus";

        String prompt = awardAssistPromptTemplate
            .replace("{{collegeName}}", collegeName)
            .replace("{{major}}", major)
            .replace("{{residency}}", residency)
            .replace("{{living}}", living)
            .replace("{{liveSearchContent}}", liveSearchContent != null && !liveSearchContent.isBlank()
                ? liveSearchContent : "(no live search results found)")
            .replace("{{coa}}",               String.format("%.0f", coa))
            .replace("{{netPrice}}",          String.format("%.0f", netPrice))
            .replace("{{unmetNeed}}",         String.format("%.0f", unmetNeed))
            .replace("{{federalLoan}}",       String.format("%.0f", federalLoan))
            .replace("{{parentPlus}}",        String.format("%.0f", parentPlus))
            .replace("{{pellGrant}}",         String.format("%.0f", pellGrant))
            .replace("{{instGrant}}",         String.format("%.0f", instGrant))
            .replace("{{scholarship}}",       String.format("%.0f", scholarship))
            .replace("{{workStudy}}",         String.format("%.0f", workStudy))
            .replace("{{majorEarnings}}",     String.format("%.0f", majorEarnings))
            .replace("{{collegeWideEarnings}}", String.format("%.0f", collegeWideEarnings));

        MessageCreateParams params = MessageCreateParams.builder()
            .model(awardAssistModel)
            .maxTokens(4096L)
            .addUserMessage(prompt)
            .build();

        Message response = client.messages().create(params);
        recordUsage(response, userId, sessionId, InputPayloadType.COA, awardAssistModel);
        return extractLastText(response);
    }

    /** Post-Grad Debt Relief's "AI Assist" recommendation — which repayment plan fits this
     *  borrower and why, grounded in live studentaid.gov content (fetched upstream by
     *  DebtManagementController). Previously on Groq with a 700-token cap that was too small for
     *  this schema (gpt-oss-120b's internal reasoning ate into it before the JSON body was
     *  written), so responses truncated mid-JSON, failed to parse, and the raw text got dumped
     *  into the UI as the "rationale" field. Migrated to Haiku with real headroom (2026-07-18). */
    public String getRepaymentRecommendation(DebtIntakeRequest req, List<Map<String, Object>> plans,
                                              Map<String, Object> pslfResult, String liveSearchContent,
                                              Long userId, String sessionId) {
        if (client == null) {
            return "{\"recommendedPlan\":\"SAVE (formerly REPAYE)\",\"rationale\":\"Given your income relative to your loan balance, SAVE provides the lowest monthly payment and caps unpaid interest from growing your principal.\",\"keyInsight\":\"Your debt-to-income ratio is high — income-driven repayment is essential to avoid default.\",\"pslfNote\":null,\"warningFlag\":null}";
        }

        tokenUsageService.checkCapOrThrow(userId);

        String plansJson;
        try {
            plansJson = objectMapper.writeValueAsString(plans);
        } catch (Exception e) {
            plansJson = "[]";
        }
        String pslfStatus = "unknown";
        if (pslfResult != null && pslfResult.get("message") != null) {
            pslfStatus = String.valueOf(pslfResult.get("message"));
        }

        String prompt = postgradRepaymentPromptTemplate
            .replace("{{federalBalance}}", String.format("%.0f", req.getFederalLoanBalance() != null ? req.getFederalLoanBalance() : 0))
            .replace("{{privateBalance}}", String.format("%.0f", req.getPrivateLoanBalance() != null ? req.getPrivateLoanBalance() : 0))
            .replace("{{income}}", String.format("%.0f", req.getAnnualGrossIncome() != null ? req.getAnnualGrossIncome() : 0))
            .replace("{{householdSize}}", String.valueOf(req.getHouseholdSize() != null ? req.getHouseholdSize() : 1))
            .replace("{{maritalStatus}}", req.getMaritalStatus() != null ? req.getMaritalStatus() : "not provided")
            .replace("{{employmentStatus}}", req.getEmploymentStatus() != null ? req.getEmploymentStatus() : "not provided")
            .replace("{{employerName}}", req.getEmployerName() != null ? req.getEmployerName() : "not provided")
            .replace("{{creditBand}}", req.getCreditScoreBand() != null ? req.getCreditScoreBand() : "not provided")
            .replace("{{servicer}}", req.getLoanServicer() != null ? req.getLoanServicer() : "not provided")
            .replace("{{pslfStatus}}", pslfStatus)
            .replace("{{plansJson}}", plansJson)
            .replace("{{liveSearchContent}}", liveSearchContent != null ? liveSearchContent : "(No live content retrieved)");

        MessageCreateParams params = MessageCreateParams.builder()
            .model(postgradModel)
            .maxTokens(2048L)
            .addUserMessage(prompt)
            .build();

        Message response = client.messages().create(params);
        recordUsage(response, userId, sessionId, InputPayloadType.POSTGRAD, postgradModel);
        return extractLastText(response);
    }

    /** State-Specific Assistance section — bullet-point programs grounded in live search and
     *  scoped to the borrower's actual federal/private balances (not a generic state overview).
     *  Previously on Groq, generic to the state only, "3-5 paragraphs" free text. */
    public String getStateAssistanceSummary(String state, Double federalBalance, Double privateBalance,
                                             Double income, String liveSearchContent,
                                             Long userId, String sessionId) {
        if (client == null) {
            return "{\"federalPrograms\":[],\"privateRefinancing\":[],\"advocacy\":[\"Dev-mode placeholder — " + state + " advocacy org.\"]}";
        }

        tokenUsageService.checkCapOrThrow(userId);

        String prompt = postgradStateAssistancePromptTemplate
            .replace("{{state}}", state)
            .replace("{{federalBalance}}", String.format("%.0f", federalBalance != null ? federalBalance : 0))
            .replace("{{privateBalance}}", String.format("%.0f", privateBalance != null ? privateBalance : 0))
            .replace("{{income}}", String.format("%.0f", income != null ? income : 0))
            .replace("{{liveSearchContent}}", liveSearchContent != null ? liveSearchContent : "(No live content retrieved)");

        MessageCreateParams params = MessageCreateParams.builder()
            .model(postgradModel)
            .maxTokens(1536L)
            .addUserMessage(prompt)
            .build();

        Message response = client.messages().create(params);
        recordUsage(response, userId, sessionId, InputPayloadType.POSTGRAD, postgradModel);
        return extractLastText(response);
    }

    /** Condenses a list of raw Tavily search results (title/snippet/url) about one topic into a
     *  3-5 sentence summary — used for Private Lender Research's forbearance-help and
     *  repayment-assistance-FAQ sections, which previously dumped every raw snippet onto the
     *  page unsummarized. */
    public String summarizeSearchResults(String topic, List<Map<String, Object>> results,
                                          Long userId, String sessionId) {
        if (results == null || results.isEmpty()) {
            return "No live search results were found for " + topic + ".";
        }
        if (client == null) {
            return "Dev-mode placeholder summary for " + topic + ".";
        }

        tokenUsageService.checkCapOrThrow(userId);

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : results) {
            Object content = r.get("content");
            if (content == null) content = r.get("snippet");
            sb.append("- ").append(r.getOrDefault("url", "")).append(": ").append(content).append("\n");
        }

        String prompt = postgradSearchSummaryPromptTemplate
            .replace("{{topic}}", topic)
            .replace("{{searchResults}}", sb.toString().trim());

        MessageCreateParams params = MessageCreateParams.builder()
            .model(postgradModel)
            .maxTokens(512L)
            .addUserMessage(prompt)
            .build();

        Message response = client.messages().create(params);
        recordUsage(response, userId, sessionId, InputPayloadType.POSTGRAD, postgradModel);
        String raw = extractLastText(response);
        try {
            Map<?, ?> parsed = objectMapper.readValue(GroqService.stripMarkdownFences(raw), Map.class);
            Object summary = parsed.get("summary");
            return summary != null ? summary.toString() : raw;
        } catch (Exception e) {
            return raw;
        }
    }

    /** Ask Astra's chat response. Scoped to the logged-in user's OWN saved data only, via the
     *  {@code get_my_saved_data_history} tool (backed by {@link ChatbotContextService}, read-only,
     *  no write access exposed to the model) — this is how the bot answers things like "what was
     *  my parental AGI" with an actual history of values across saved sessions, instead of the
     *  counts-only summary that was baked into the prompt before. Live search content (already
     *  fetched via Tavily upstream by ChatController) is injected into the system prompt for
     *  general financial-aid questions the tool can't answer. userId/sessionId are always
     *  resolved server-side by the caller — never accepted from the client. */
    public String getChatResponse(List<Map<String, Object>> history, String userMessage, String liveSearchContent,
                                   Long userId, String sessionId) {
        if (client == null) {
            return "This is a dev-mode placeholder response from Ask Astra. Set ANTHROPIC_API_KEY to get real answers.";
        }

        tokenUsageService.checkCapOrThrow(userId);

        Tool historyTool = Tool.builder()
            .name(CHATBOT_TOOL_NAME)
            .description("Looks up the logged-in student's OWN previously saved data in this app (FAFSA Prep, "
                + "Scholarships, Award Assist/Cost-of-Attendance, Post-Grad Debt Relief) so you can answer "
                + "questions about figures they entered before, e.g. \"what was my parental AGI\" or \"what "
                + "income did I use last time\". Read-only — call this whenever the user references data they "
                + "previously saved, rather than guessing or asking them to repeat it. Returns every saved "
                + "session for the category, newest first, so you can describe how a value changed over time.")
            .inputSchema(Tool.InputSchema.builder()
                .properties(Tool.InputSchema.Properties.builder()
                    .putAdditionalProperty("category", JsonValue.from(Map.of(
                        "type", "string",
                        "enum", List.of("fafsa", "scholarship", "coa", "postgrad", "all"),
                        "description", "Which saved-data category to look up. Use \"all\" if you're not sure which tab the data came from."
                    )))
                    .build())
                .required(List.of("category"))
                .build())
            .build();

        String system = "You are Astra, a financial-aid assistant for college students inside the Astra app. "
            + "Be concise (2-4 sentences unless the user asks for more detail). Only answer using the "
            + CHATBOT_TOOL_NAME + " tool's results, the live search content below, and general financial-aid "
            + "knowledge — never fabricate a figure the user hasn't provided or the tool hasn't returned. "
            + "You have NO write access to any of the user's data: you can only look it up, never change, "
            + "delete, or save anything. If asked to modify data, explain that Ask Astra is read-only and "
            + "point the user to the relevant tab.\n\nLive search results:\n"
            + (liveSearchContent != null && !liveSearchContent.isBlank() ? liveSearchContent : "(none)");

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
            .model(chatbotModel)
            .maxTokens(1024L)
            .system(system)
            .addTool(historyTool);

        for (Map<String, Object> turn : history) {
            Object content = turn.get("content");
            if (content == null) continue;
            if ("assistant".equals(turn.get("role"))) {
                builder.addAssistantMessage(content.toString());
            } else {
                builder.addUserMessage(content.toString());
            }
        }
        builder.addUserMessage(userMessage);

        long totalIn = 0, totalOut = 0;
        Message response = client.messages().create(builder.build());
        totalIn += response.usage().inputTokens();
        totalOut += response.usage().outputTokens();

        int iterations = 0;
        while (response.stopReason().isPresent() && response.stopReason().get().equals(StopReason.TOOL_USE)
                && iterations++ < CHATBOT_MAX_TOOL_ITERATIONS) {
            builder.addMessage(response);
            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                block.toolUse().ifPresent(tu -> {
                    String category = "all";
                    try {
                        Map<?, ?> input = tu._input().convert(Map.class);
                        if (input != null && input.get("category") != null) {
                            category = input.get("category").toString();
                        }
                    } catch (Exception ignored) {}
                    String result = chatbotContextService.getSavedDataHistoryForTool(userId, category);
                    toolResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(tu.id())
                        .content(result)
                        .build()));
                });
            }
            builder.addMessage(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(toolResults)
                .build());
            response = client.messages().create(builder.build());
            totalIn += response.usage().inputTokens();
            totalOut += response.usage().outputTokens();
        }

        try {
            tokenUsageService.recordUsage(userId, sessionId, InputPayloadType.CHATBOT, chatbotModel, totalIn, totalOut);
        } catch (Exception ignored) {
            // Never fail the actual request over usage bookkeeping.
        }

        return extractLastText(response);
    }

    private void recordUsage(Message response, Long userId, String sessionId, InputPayloadType type, String modelUsed) {
        try {
            tokenUsageService.recordUsage(userId, sessionId, type, modelUsed,
                response.usage().inputTokens(), response.usage().outputTokens());
        } catch (Exception e) {
            // Never fail the actual request over usage bookkeeping.
        }
    }

    private String extractLastText(Message response) {
        String lastText = null;
        for (ContentBlock block : response.content()) {
            if (block.text().isPresent()) {
                lastText = block.text().get().text();
            }
        }
        return lastText != null ? lastText : "";
    }

    /** Returns the chapter labels + source URLs currently seeded for this award year, so the
     *  UI can show which rule set an analysis is grounded in. */
    public List<Map<String, String>> getHandbookSources(String awardYear) {
        return handbookReferenceRepository.findByAwardYearOrderById(awardYear).stream()
            .map(ref -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("chapterLabel", ref.getChapterLabel());
                m.put("sourceUrl", ref.getSourceUrl());
                return m;
            })
            .collect(Collectors.toList());
    }

    private String buildHandbookContent(String awardYear) {
        List<FafsaHandbookReference> refs = handbookReferenceRepository.findByAwardYearOrderById(awardYear);
        if (refs.isEmpty()) {
            return "(No hardcoded handbook reference found for award year " + awardYear
                + " — reason from training knowledge of the FSA Handbook, with appropriate uncertainty.)";
        }
        StringBuilder sb = new StringBuilder();
        for (FafsaHandbookReference ref : refs) {
            sb.append("\n[").append(ref.getChapterLabel()).append("]\n");
            sb.append(ref.getContent()).append("\n");
            if (ref.getSourceUrl() != null) {
                sb.append("Source: ").append(ref.getSourceUrl()).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
