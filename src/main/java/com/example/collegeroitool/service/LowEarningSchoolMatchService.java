package com.example.collegeroitool.service;

import com.example.collegeroitool.model.LowEarningSchoolEarnings;
import com.example.collegeroitool.repository.LowEarningSchoolEarningsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Resolves a user-typed college name to its row in {@link LowEarningSchoolEarningsRepository}.
 *  Fast path: exact match on the normalized name (covers the common case, since this dataset's
 *  institution names come from the same College Scorecard source as the rest of this app).
 *  Fallback: narrow candidates by a shared name token, then let Claude disambiguate — school
 *  names are exactly the kind of natural-language matching problem AI reasoning suits (e.g.
 *  "UMass Amherst" vs "University of Massachusetts Amherst", word-order variants), unlike the
 *  deterministic financial figures elsewhere in Award Assist which must never go through an LLM. */
@Service
public class LowEarningSchoolMatchService {

    private static final int MAX_CANDIDATES = 25;

    private final LowEarningSchoolEarningsRepository repository;
    private final AnthropicService anthropicService;

    public LowEarningSchoolMatchService(LowEarningSchoolEarningsRepository repository,
                                         AnthropicService anthropicService) {
        this.repository = repository;
        this.anthropicService = anthropicService;
    }

    public Optional<LowEarningSchoolEarnings> match(String collegeName, Long userId, String sessionId) {
        if (collegeName == null || collegeName.isBlank()) return Optional.empty();

        String normalized = SchoolNameNormalizer.normalize(collegeName);
        Optional<LowEarningSchoolEarnings> exact = repository.findByNormalizedName(normalized);
        if (exact.isPresent()) return exact;

        String token = SchoolNameNormalizer.firstSignificantToken(normalized);
        if (token.isBlank()) return Optional.empty();

        List<LowEarningSchoolEarnings> candidates = repository.findByNormalizedNameContaining(token);
        if (candidates.isEmpty()) return Optional.empty();
        if (candidates.size() > MAX_CANDIDATES) candidates = candidates.subList(0, MAX_CANDIDATES);

        Optional<Integer> matchedUnitId = anthropicService.matchSchoolName(collegeName, candidates, userId, sessionId);
        if (matchedUnitId.isEmpty()) return Optional.empty();

        // Only trust a unitId the AI actually chose from the candidate list handed to it.
        Integer unitId = matchedUnitId.get();
        return candidates.stream().filter(c -> unitId.equals(c.getUnitId())).findFirst();
    }
}
