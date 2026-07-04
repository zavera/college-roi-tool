package com.example.collegeroitool.service;

import com.example.collegeroitool.repository.AppConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class AppConfigService {

    private static final String SUBSCRIPTION_AMOUNT_CENTS_KEY = "subscription_amount_cents";
    private static final int DEFAULT_SUBSCRIPTION_AMOUNT_CENTS = 9900;

    private static final String FREE_SEARCHES_LIMIT_KEY = "free_searches_limit";
    private static final int DEFAULT_FREE_SEARCHES_LIMIT = 3;

    private final AppConfigRepository repo;

    public AppConfigService(AppConfigRepository repo) {
        this.repo = repo;
    }

    public int getSubscriptionAmountCents() {
        return getIntConfig(SUBSCRIPTION_AMOUNT_CENTS_KEY, DEFAULT_SUBSCRIPTION_AMOUNT_CENTS);
    }

    /** Number of free searches allowed per tab (fafsa/scholarship/coa/postgrad) before the paywall shows. */
    public int getFreeSearchesLimit() {
        return getIntConfig(FREE_SEARCHES_LIMIT_KEY, DEFAULT_FREE_SEARCHES_LIMIT);
    }

    private int getIntConfig(String key, int defaultValue) {
        return repo.findByConfigKey(key)
            .map(c -> {
                try { return Integer.parseInt(c.getConfigValue()); }
                catch (NumberFormatException e) { return defaultValue; }
            })
            .orElse(defaultValue);
    }
}
