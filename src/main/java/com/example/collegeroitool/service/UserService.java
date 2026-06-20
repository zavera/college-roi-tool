package com.example.collegeroitool.service;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.repository.UserRepository;
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

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

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

    public AppUser registerLocal(String name, String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        AppUser user = new AppUser();
        user.setEmail(email.toLowerCase().trim());
        user.setName(name.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setProvider("local");
        return userRepository.save(user);
    }

    public AppUser findOrCreateGoogleUser(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name  = oAuth2User.getAttribute("name");
        if (email == null) throw new IllegalStateException("Google account has no email address.");

        Optional<AppUser> existing = userRepository.findByEmail(email.toLowerCase());
        if (existing.isPresent()) return existing.get();

        AppUser user = new AppUser();
        user.setEmail(email.toLowerCase());
        user.setName(name != null ? name : email);
        user.setProvider("google");
        return userRepository.save(user);
    }

    public Optional<AppUser> findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase());
    }

    public AppUser findOrCreateDevUser() {
        return userRepository.findByEmail("dev@local").orElseGet(() -> {
            AppUser nu = new AppUser();
            nu.setEmail("dev@local");
            nu.setName("Dev User");
            nu.setProvider("local");
            nu.setSubscriptionActive(true);
            return userRepository.save(nu);
        });
    }

    public boolean deactivateSubscription(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setSubscriptionActive(false);
            userRepository.save(u);
            return true;
        }).orElse(false);
    }

    public boolean activateSubscription(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setSubscriptionActive(true);
            userRepository.save(u);
            return true;
        }).orElse(false);
    }

    public int incrementSearchCount(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setSearchCount(u.getSearchCount() + 1);
            userRepository.save(u);
            return u.getSearchCount();
        }).orElse(-1);
    }

    public int incrementDebtSearchCount(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setDebtSearchCount(u.getDebtSearchCount() + 1);
            userRepository.save(u);
            return u.getDebtSearchCount();
        }).orElse(-1);
    }

    public int incrementScholarshipSearchCount(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setScholarshipSearchCount(u.getScholarshipSearchCount() + 1);
            userRepository.save(u);
            return u.getScholarshipSearchCount();
        }).orElse(-1);
    }

    public int incrementFafsaUsageCount(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setFafsaUsageCount(u.getFafsaUsageCount() + 1);
            userRepository.save(u);
            return u.getFafsaUsageCount();
        }).orElse(-1);
    }

    public Optional<Boolean> toggleSubscription(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            boolean newState = !u.isSubscriptionActive();
            u.setSubscriptionActive(newState);
            userRepository.save(u);
            return newState;
        });
    }
}
