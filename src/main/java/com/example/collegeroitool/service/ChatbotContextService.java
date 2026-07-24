package com.example.collegeroitool.service;

import com.example.collegeroitool.model.Coa;
import com.example.collegeroitool.model.Fafsa;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.Postgrad;
import com.example.collegeroitool.model.Scholarship;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.model.Startup;
import com.example.collegeroitool.model.Subscription;
import com.example.collegeroitool.repository.CoaRepository;
import com.example.collegeroitool.repository.FafsaRepository;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.repository.PostgradRepository;
import com.example.collegeroitool.repository.ScholarshipRepository;
import com.example.collegeroitool.repository.SearchUsageRepository;
import com.example.collegeroitool.repository.StartupRepository;
import com.example.collegeroitool.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles a per-user data summary for the chatbot only — never for the other
 * FERPA-scoped features. Only computed values (counts, flags) are included,
 * never raw stored payloads, so no PII is injected into the chat prompt.
 */
@Service
public class ChatbotContextService {

    private final SubscriptionRepository subscriptionRepository;
    private final SearchUsageRepository searchUsageRepository;
    private final FafsaRepository fafsaRepository;
    private final ScholarshipRepository scholarshipRepository;
    private final CoaRepository coaRepository;
    private final PostgradRepository postgradRepository;
    private final StartupRepository startupRepository;
    private final ModelResponseRepository modelResponseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatbotContextService(SubscriptionRepository subscriptionRepository,
                                  SearchUsageRepository searchUsageRepository,
                                  FafsaRepository fafsaRepository,
                                  ScholarshipRepository scholarshipRepository,
                                  CoaRepository coaRepository,
                                  PostgradRepository postgradRepository,
                                  StartupRepository startupRepository,
                                  ModelResponseRepository modelResponseRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.searchUsageRepository = searchUsageRepository;
        this.fafsaRepository = fafsaRepository;
        this.scholarshipRepository = scholarshipRepository;
        this.coaRepository = coaRepository;
        this.postgradRepository = postgradRepository;
        this.startupRepository = startupRepository;
        this.modelResponseRepository = modelResponseRepository;
    }

    /** Builds a plain-text summary of the given user's own data, for prompt injection only. */
    public String buildContextSummary(Long userId) {
        StringBuilder sb = new StringBuilder();

        Subscription sub = subscriptionRepository.findByUserId(userId).orElse(null);
        sb.append("Subscription active: ").append(sub != null && sub.isActive()).append("\n");

        SearchUsage usage = searchUsageRepository.findByUserId(userId).orElse(null);
        sb.append("Search usage — fafsa: ").append(usage != null ? usage.getFafsa() : 0)
          .append(", scholarship: ").append(usage != null ? usage.getScholarship() : 0)
          .append(", coa: ").append(usage != null ? usage.getCoa() : 0)
          .append(", postgrad: ").append(usage != null ? usage.getPostgrad() : 0)
          .append(", startup: ").append(usage != null ? usage.getStartup() : 0)
          .append("\n");

        List<Fafsa> fafsaRows = fafsaRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Scholarship> scholarshipRows = scholarshipRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Coa> coaRows = coaRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Postgrad> postgradRows = postgradRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Startup> startupRows = startupRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        sb.append("FAFSA sessions on file: ").append(fafsaRows.size()).append("\n");
        sb.append("Scholarship searches on file: ").append(scholarshipRows.size()).append("\n");
        sb.append("Cost-of-attendance sessions on file: ").append(coaRows.size()).append("\n");
        sb.append("Post-grad sessions on file: ").append(postgradRows.size()).append("\n");
        sb.append("Startup Locator searches on file: ").append(startupRows.size()).append("\n");

        int modelResponses =
            modelResponseRepository.findByTypeInputPayloadAndInputIdIn(InputPayloadType.FAFSA, fafsaRows.stream().map(Fafsa::getId).toList()).size()
            + modelResponseRepository.findByTypeInputPayloadAndInputIdIn(InputPayloadType.SCHOLARSHIP, scholarshipRows.stream().map(Scholarship::getId).toList()).size()
            + modelResponseRepository.findByTypeInputPayloadAndInputIdIn(InputPayloadType.COA, coaRows.stream().map(Coa::getId).toList()).size()
            + modelResponseRepository.findByTypeInputPayloadAndInputIdIn(InputPayloadType.POSTGRAD, postgradRows.stream().map(Postgrad::getId).toList()).size();
        sb.append("AI responses generated so far: ").append(modelResponses).append("\n");

        return sb.toString();
    }

    /** Backs the chatbot's "get_my_saved_data_history" tool. Returns this user's own saved
     *  session payloads (newest first) for the requested category, so the model can answer
     *  questions like "what was my parental AGI" with an actual history of values across
     *  sessions instead of a summary count. userId always comes from the server-side session in
     *  ChatController/AnthropicService — this method has no path for a client to supply another
     *  user's ID. Read-only: there is no corresponding write method exposed to the model. */
    public String getSavedDataHistoryForTool(Long userId, String category) {
        String cat = category == null ? "all" : category.toLowerCase();
        List<Map<String, Object>> entries = new ArrayList<>();

        if (cat.equals("fafsa") || cat.equals("all")) {
            for (Fafsa f : fafsaRepository.findAllByUserIdOrderByCreatedAtDesc(userId)) {
                entries.add(toEntry("fafsa", f.getCreatedAt(), f.getInputFafsaPayload()));
            }
        }
        if (cat.equals("scholarship") || cat.equals("all")) {
            for (Scholarship s : scholarshipRepository.findAllByUserIdOrderByCreatedAtDesc(userId)) {
                entries.add(toEntry("scholarship", s.getCreatedAt(), s.getInputScholarshipPayload()));
            }
        }
        if (cat.equals("coa") || cat.equals("all")) {
            for (Coa c : coaRepository.findAllByUserIdOrderByCreatedAtDesc(userId)) {
                entries.add(toEntry("coa", c.getCreatedAt(), c.getInputCoaPayload()));
            }
        }
        if (cat.equals("postgrad") || cat.equals("all")) {
            for (Postgrad p : postgradRepository.findAllByUserIdOrderByCreatedAtDesc(userId)) {
                entries.add(toEntry("postgrad", p.getCreatedAt(), p.getInputPostgradPayload()));
            }
        }
        if (cat.equals("startup") || cat.equals("all")) {
            for (Startup s : startupRepository.findAllByUserIdOrderByCreatedAtDesc(userId)) {
                entries.add(toEntry("startup", s.getCreatedAt(), s.getInputStartupPayload()));
            }
        }

        if (entries.isEmpty()) {
            return "No saved \"" + cat + "\" data found for this user.";
        }
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            return "Error retrieving saved data.";
        }
    }

    private Map<String, Object> toEntry(String type, LocalDateTime createdAt, String payloadJson) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("createdAt", createdAt != null ? createdAt.toString() : null);
        try {
            m.put("payload", objectMapper.readValue(payloadJson, Object.class));
        } catch (Exception e) {
            m.put("payload", payloadJson);
        }
        return m;
    }
}
