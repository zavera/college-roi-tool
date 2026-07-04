package com.example.collegeroitool.service;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.SearchUsage;
import com.example.collegeroitool.repository.SearchUsageRepository;
import org.springframework.stereotype.Service;

@Service
public class SearchUsageService {

    private final SearchUsageRepository repo;

    public SearchUsageService(SearchUsageRepository repo) {
        this.repo = repo;
    }

    /** Returns the user's search usage row, creating a zeroed one on first login. */
    public SearchUsage getOrCreateForUser(AppUser user) {
        return repo.findByUserId(user.getId()).orElseGet(() -> {
            SearchUsage usage = new SearchUsage();
            usage.setUserId(user.getId());
            return repo.save(usage);
        });
    }
}
