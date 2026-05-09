package com.example.collegeroitool.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionService {

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public boolean isActive() {
        return devBypass;
        // Future: check DB — return repo.existsByEmailAndStatus(email, "active");
    }
}
