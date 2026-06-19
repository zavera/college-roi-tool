package com.example.collegeroitool.service;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InstitutionService institutionService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       @Lazy InstitutionService institutionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.institutionService = institutionService;
    }

    /** Called by Spring Security for email/password login */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("No account found for: " + email));
        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPasswordHash() != null ? user.getPasswordHash() : "",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    /** Register a new local (email/password) user */
    public AppUser registerLocal(String name, String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        AppUser user = new AppUser();
        user.setEmail(email.toLowerCase().trim());
        user.setName(name.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setProvider("local");
        AppUser saved = userRepository.save(user);
        try { institutionService.ensureDefaultEnrollment(saved); } catch (Exception ignored) {}
        return saved;
    }

    /** Find or create a user from a Google OAuth2 login */
    public AppUser findOrCreateGoogleUser(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name  = oAuth2User.getAttribute("name");
        if (email == null) throw new IllegalStateException("Google account has no email address.");

        Optional<AppUser> existing = userRepository.findByEmail(email.toLowerCase());
        if (existing.isPresent()) {
            AppUser u = existing.get();
            try { institutionService.ensureDefaultEnrollment(u); } catch (Exception ignored) {}
            return u;
        }
        AppUser user = new AppUser();
        user.setEmail(email.toLowerCase());
        user.setName(name != null ? name : email);
        user.setProvider("google");
        AppUser saved = userRepository.save(user);
        try { institutionService.ensureDefaultEnrollment(saved); } catch (Exception ignored) {}
        return saved;
    }

    public Optional<AppUser> findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase());
    }

    /** Local-dev-only helper — synthesizes a persistable "dev@local" account so FAFSA Prep
     *  data can be saved when premium.dev.bypass is on and there's no real session. */
    public AppUser findOrCreateDevUser() {
        AppUser u = userRepository.findByEmail("dev@local").orElseGet(() -> {
            AppUser nu = new AppUser();
            nu.setEmail("dev@local");
            nu.setName("Dev User");
            nu.setProvider("local");
            nu.setSubscriptionActive(true);
            return userRepository.save(nu);
        });
        try { institutionService.ensureDefaultEnrollment(u); } catch (Exception ignored) {}
        return u;
    }

    /** Deactivate subscription — called when Stripe subscription is cancelled */
    public boolean deactivateSubscription(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setSubscriptionActive(false);
            userRepository.save(u);
            return true;
        }).orElse(false);
    }

    /** Admin helper — activate a subscription by email */
    public boolean activateSubscription(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setSubscriptionActive(true);
            userRepository.save(u);
            return true;
        }).orElse(false);
    }

    /** Increment search count; returns new count, or -1 if user not found */
    public int incrementSearchCount(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setSearchCount(u.getSearchCount() + 1);
            userRepository.save(u);
            return u.getSearchCount();
        }).orElse(-1);
    }

    /** Increment debt search count; returns new count, or -1 if user not found */
    public int incrementDebtSearchCount(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setDebtSearchCount(u.getDebtSearchCount() + 1);
            userRepository.save(u);
            return u.getDebtSearchCount();
        }).orElse(-1);
    }

    /** Increment FAFSA Prep module usage count; returns new count, or -1 if user not found */
    public int incrementFafsaUsageCount(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setFafsaUsageCount(u.getFafsaUsageCount() + 1);
            userRepository.save(u);
            return u.getFafsaUsageCount();
        }).orElse(-1);
    }

    /** Toggle subscription on/off; returns the new state, or empty if user not found */
    public java.util.Optional<Boolean> toggleSubscription(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            boolean newState = !u.isSubscriptionActive();
            u.setSubscriptionActive(newState);
            userRepository.save(u);
            return newState;
        });
    }
}
