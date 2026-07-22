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

    public SearchUsage incrementFafsa(AppUser user) {
        SearchUsage usage = getOrCreateForUser(user);
        usage.setFafsa(usage.getFafsa() + 1);
        return repo.save(usage);
    }

    public SearchUsage incrementScholarship(AppUser user) {
        SearchUsage usage = getOrCreateForUser(user);
        usage.setScholarship(usage.getScholarship() + 1);
        return repo.save(usage);
    }

    public SearchUsage incrementCoa(AppUser user) {
        SearchUsage usage = getOrCreateForUser(user);
        usage.setCoa(usage.getCoa() + 1);
        return repo.save(usage);
    }

    public SearchUsage incrementPostgrad(AppUser user) {
        SearchUsage usage = getOrCreateForUser(user);
        usage.setPostgrad(usage.getPostgrad() + 1);
        return repo.save(usage);
    }

    public SearchUsage incrementStartup(AppUser user) {
        SearchUsage usage = getOrCreateForUser(user);
        usage.setStartup(usage.getStartup() + 1);
        return repo.save(usage);
    }
}
