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
        return userRepository.save(user);
    }

    /** Find or create a user from a Google OAuth2 login */
    public AppUser findOrCreateGoogleUser(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name  = oAuth2User.getAttribute("name");
        if (email == null) throw new IllegalStateException("Google account has no email address.");

        Optional<AppUser> existing = userRepository.findByEmail(email.toLowerCase());
        if (existing.isPresent()) {
            return existing.get();
        }
        AppUser user = new AppUser();
        user.setEmail(email.toLowerCase());
        user.setName(name != null ? name : email);
        user.setProvider("google");
        return userRepository.save(user);
    }

    public Optional<AppUser> findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase());
    }

    /** Admin helper — activate a subscription by email */
    public boolean activateSubscription(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(u -> {
            u.setSubscriptionActive(true);
            userRepository.save(u);
            return true;
        }).orElse(false);
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
