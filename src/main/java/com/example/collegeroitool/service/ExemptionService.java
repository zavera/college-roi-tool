package com.example.collegeroitool.service;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.Exemption;
import com.example.collegeroitool.repository.ExemptionRepository;
import org.springframework.stereotype.Service;

@Service
public class ExemptionService {

    private final ExemptionRepository repo;

    public ExemptionService(ExemptionRepository repo) {
        this.repo = repo;
    }

    /** Returns the user's exemption row, creating a default (inactive) one on first login. */
    public Exemption getOrCreateForUser(AppUser user) {
        return repo.findByUserId(user.getId()).orElseGet(() -> {
            Exemption exemption = new Exemption();
            exemption.setUserId(user.getId());
            exemption.setActive(false);
            return repo.save(exemption);
        });
    }
}
