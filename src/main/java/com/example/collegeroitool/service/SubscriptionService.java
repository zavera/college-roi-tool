package com.example.collegeroitool.service;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.Subscription;
import com.example.collegeroitool.repository.SubscriptionRepository;
import com.example.collegeroitool.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionService {

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private final SubscriptionRepository repo;
    private final UserRepository userRepository;
    private final AppConfigService appConfigService;
    private final ExemptionService exemptionService;

    public SubscriptionService(SubscriptionRepository repo, UserRepository userRepository,
                                AppConfigService appConfigService, ExemptionService exemptionService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.appConfigService = appConfigService;
        this.exemptionService = exemptionService;
    }

    /** True if the user has a paid subscription OR a manually granted exemption. */
    public boolean hasAccess(AppUser user) {
        return getOrCreateForUser(user).isActive() || exemptionService.getOrCreateForUser(user).isActive();
    }

    /** Returns the user's subscription row, creating a default (inactive) one on first login. */
    public Subscription getOrCreateForUser(AppUser user) {
        return repo.findByUserId(user.getId()).orElseGet(() -> {
            Subscription sub = new Subscription();
            sub.setUserId(user.getId());
            sub.setActive(false);
            sub.setAmountCents(appConfigService.getSubscriptionAmountCents());
            return repo.save(sub);
        });
    }

    public boolean toggleActive(AppUser user) {
        Subscription sub = getOrCreateForUser(user);
        sub.setActive(!sub.isActive());
        return repo.save(sub).isActive();
    }

    public boolean setActive(AppUser user, boolean active) {
        Subscription sub = getOrCreateForUser(user);
        sub.setActive(active);
        return repo.save(sub).isActive();
    }

    public boolean isActive() {
        if (devBypass) return true;

        String email = currentUserEmail();
        if (email == null) return false;

        return userRepository.findByEmail(email)
            .map(this::hasAccess)
            .orElse(false);
    }

    private String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;

        Object principal = auth.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        }
        // Local (email/password) users: getName() returns the email
        String name = auth.getName();
        return "anonymousUser".equals(name) ? null : name;
    }
}
