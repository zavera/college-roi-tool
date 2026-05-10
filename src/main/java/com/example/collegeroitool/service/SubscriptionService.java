package com.example.collegeroitool.service;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SubscriptionService {

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private final UserRepository userRepository;

    public SubscriptionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isActive() {
        if (devBypass) return true;

        String email = currentUserEmail();
        if (email == null) return false;

        return userRepository.findByEmail(email)
            .map(AppUser::isSubscriptionActive)
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
