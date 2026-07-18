package com.example.collegeroitool.service;

import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.TokenUsage;
import com.example.collegeroitool.repository.TokenUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** Records token usage for every Claude API call and enforces a per-user monthly spend cap.
 *  Anthropic's Messages API returns input_tokens/output_tokens on every response but no dollar
 *  figure — cost is computed here from each model's current per-token price. Scoped to Claude
 *  (Anthropic) usage only; Groq calls are not tracked or capped by this service. */
@Service
public class TokenUsageService {

    /** $ per 1M tokens, {inputPrice, outputPrice}. Anthropic's standard (non-intro) rates as of
     *  the 2026-06-24 pricing snapshot — update if Anthropic changes pricing. Unknown models fall
     *  back to the Sonnet-tier rate so a misconfigured model name doesn't silently under-charge. */
    private static final Map<String, double[]> PRICE_PER_MILLION_TOKENS = Map.of(
        "claude-haiku-4-5", new double[]{1.00, 5.00},
        "claude-sonnet-5",  new double[]{3.00, 15.00},
        "claude-opus-4-8",  new double[]{5.00, 25.00}
    );
    private static final double[] FALLBACK_PRICE = PRICE_PER_MILLION_TOKENS.get("claude-sonnet-5");

    @Value("${token.usage.monthly.cap.usd:20.00}")
    private double monthlyCapUsd;

    private final TokenUsageRepository tokenUsageRepository;

    public TokenUsageService(TokenUsageRepository tokenUsageRepository) {
        this.tokenUsageRepository = tokenUsageRepository;
    }

    /** Throws if this user has already reached/exceeded their monthly cap. Call before making
     *  the Claude API call, not just before recording it — otherwise the cap can be exceeded by
     *  however many concurrent requests were already in flight. */
    public void checkCapOrThrow(Long userId) {
        if (userId == null) return;
        double spent = getMonthlySpendUsd(userId);
        if (spent >= monthlyCapUsd) {
            throw new MonthlyCostCapExceededException(
                String.format("Monthly AI usage cap of $%.2f reached (current: $%.2f). " +
                    "This resets at the start of next month.", monthlyCapUsd, spent));
        }
    }

    /** Persists one usage row. Safe to call with a null userId (e.g. unauthenticated) — it's a
     *  no-op, matching how this codebase skips persistence elsewhere for anonymous requests. */
    public void recordUsage(Long userId, String sessionId, InputPayloadType searchType,
                             String modelName, long tokenIn, long tokenOut) {
        if (userId == null) return;
        TokenUsage usage = new TokenUsage();
        usage.setUserId(userId);
        usage.setUserSessionId(sessionId);
        usage.setSearchTypeId(searchType);
        usage.setModelName(modelName);
        usage.setTokenIn(tokenIn);
        usage.setTokenOut(tokenOut);
        tokenUsageRepository.save(usage);
    }

    /** Sums this calendar month's Claude spend for a user, in USD. */
    public double getMonthlySpendUsd(Long userId) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return tokenUsageRepository.findByUserIdAndDateCreatedGreaterThanEqual(userId, startOfMonth)
            .stream()
            .mapToDouble(u -> costUsd(u.getModelName(), u.getTokenIn(), u.getTokenOut()))
            .sum();
    }

    private double costUsd(String modelName, long tokenIn, long tokenOut) {
        double[] price = PRICE_PER_MILLION_TOKENS.getOrDefault(modelName, FALLBACK_PRICE);
        return (tokenIn / 1_000_000.0) * price[0] + (tokenOut / 1_000_000.0) * price[1];
    }
}
