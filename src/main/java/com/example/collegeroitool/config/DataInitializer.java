package com.example.collegeroitool.config;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a pre-activated AppSumo demo account on every startup.
 * Idempotent — skips creation if the email already exists.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final String DEMO_EMAIL    = "demo@callistotech.org";
    private static final String DEMO_NAME     = "Callisto Demo";
    private static final String DEMO_PASSWORD = "CallistoDemo2025!";

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(DEMO_EMAIL)) {
            return;
        }
        AppUser demo = new AppUser();
        demo.setEmail(DEMO_EMAIL);
        demo.setName(DEMO_NAME);
        demo.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        demo.setProvider("local");
        demo.setSubscriptionActive(true);
        userRepository.save(demo);
    }
}
