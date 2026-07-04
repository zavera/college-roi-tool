package com.example.collegeroitool.service;

import com.example.collegeroitool.model.Coa;
import com.example.collegeroitool.model.Fafsa;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.Postgrad;
import com.example.collegeroitool.model.Scholarship;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.model.Subscription;
import com.example.collegeroitool.repository.CoaRepository;
import com.example.collegeroitool.repository.FafsaRepository;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.repository.PostgradRepository;
import com.example.collegeroitool.repository.ScholarshipRepository;
import com.example.collegeroitool.repository.SearchUsageRepository;
import com.example.collegeroitool.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private final ModelResponseRepository modelResponseRepository;

    public ChatbotContextService(SubscriptionRepository subscriptionRepository,
                                  SearchUsageRepository searchUsageRepository,
                                  FafsaRepository fafsaRepository,
                                  ScholarshipRepository scholarshipRepository,
                                  CoaRepository coaRepository,
                                  PostgradRepository postgradRepository,
                                  ModelResponseRepository modelResponseRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.searchUsageRepository = searchUsageRepository;
        this.fafsaRepository = fafsaRepository;
        this.scholarshipRepository = scholarshipRepository;
        this.coaRepository = coaRepository;
        this.postgradRepository = postgradRepository;
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
          .append("\n");

        List<Fafsa> fafsaRows = fafsaRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Scholarship> scholarshipRows = scholarshipRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Coa> coaRows = coaRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Postgrad> postgradRows = postgradRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        sb.append("FAFSA sessions on file: ").append(fafsaRows.size()).append("\n");
        sb.append("Scholarship searches on file: ").append(scholarshipRows.size()).append("\n");
        sb.append("Cost-of-attendance sessions on file: ").append(coaRows.size()).append("\n");
        sb.append("Post-grad sessions on file: ").append(postgradRows.size()).append("\n");

        int modelResponses =
            modelResponseRepository.findByTypeInputPayloadAndInputIdIn(InputPayloadType.FAFSA, fafsaRows.stream().map(Fafsa::getId).toList()).size()
            + modelResponseRepository.findByTypeInputPayloadAndInputIdIn(InputPayloadType.SCHOLARSHIP, scholarshipRows.stream().map(Scholarship::getId).toList()).size()
            + modelResponseRepository.findByTypeInputPayloadAndInputIdIn(InputPayloadType.COA, coaRows.stream().map(Coa::getId).toList()).size()
            + modelResponseRepository.findByTypeInputPayloadAndInputIdIn(InputPayloadType.POSTGRAD, postgradRows.stream().map(Postgrad::getId).toList()).size();
        sb.append("AI responses generated so far: ").append(modelResponses).append("\n");

        return sb.toString();
    }
}
