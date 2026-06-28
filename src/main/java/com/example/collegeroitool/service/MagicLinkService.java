package com.example.collegeroitool.service;

import com.example.collegeroitool.model.MagicLinkToken;
import com.example.collegeroitool.repository.MagicLinkTokenRepository;
import com.example.collegeroitool.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);

    private final MagicLinkTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.magic-link.expiry-minutes:15}")
    private int expiryMinutes;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public MagicLinkService(MagicLinkTokenRepository tokenRepo,
                            UserRepository userRepo,
                            JavaMailSender mailSender) {
        this.tokenRepo  = tokenRepo;
        this.userRepo   = userRepo;
        this.mailSender = mailSender;
    }

    /**
     * Generates a one-time sign-in token and emails the link to the user.
     * Returns true if the email was found and mail was sent, false if email not registered.
     */
    public boolean requestLink(String email) {
        String normalized = email.trim().toLowerCase();
        if (!userRepo.existsByEmail(normalized)) {
            // Don't reveal whether the email exists — just return false silently
            return false;
        }

        String token = UUID.randomUUID().toString();
        MagicLinkToken mlt = new MagicLinkToken();
        mlt.setToken(token);
        mlt.setEmail(normalized);
        mlt.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        tokenRepo.save(mlt);

        String link = baseUrl + "/api/auth/magic-link/verify?token=" + token;

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(normalized);
            msg.setSubject("Your Astra sign-in link");
            msg.setText(
                "Hi,\n\n" +
                "Click the link below to sign in to Astra. " +
                "It expires in " + expiryMinutes + " minutes and can only be used once.\n\n" +
                link + "\n\n" +
                "If you didn't request this, you can safely ignore this email.\n\n" +
                "— The Astra Team"
            );
            mailSender.send(msg);
            log.info("[magic-link] sent to email={}", normalized);
        } catch (Exception e) {
            log.error("[magic-link] failed to send email={} error={}", normalized, e.getMessage(), e);
            throw new RuntimeException("Failed to send sign-in email. Please try again.");
        }

        return true;
    }

    /**
     * Validates the token and returns the associated email if valid and unused.
     * Marks the token as used on success.
     */
    public Optional<String> consume(String token) {
        return tokenRepo.findByToken(token).flatMap(mlt -> {
            if (mlt.isUsed()) {
                log.warn("[magic-link] token already used");
                return Optional.empty();
            }
            if (mlt.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("[magic-link] token expired for email={}", mlt.getEmail());
                return Optional.empty();
            }
            mlt.setUsed(true);
            tokenRepo.save(mlt);
            return Optional.of(mlt.getEmail());
        });
    }
}
