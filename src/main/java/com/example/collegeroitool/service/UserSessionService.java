package com.example.collegeroitool.service;

import com.example.collegeroitool.model.UserSession;
import com.example.collegeroitool.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserSessionService {

    private final UserSessionRepository repo;

    public UserSessionService(UserSessionRepository repo) {
        this.repo = repo;
    }

    /**
     * Register a new login. Upserts by userId — overwrites any existing session token,
     * effectively kicking out any previously logged-in device for this user.
     * The old Spring session whose token no longer matches will fail validation on its next request.
     */
    @Transactional
    public void registerLogin(Long userId, String sessionToken, String ipAddress) {
        UserSession session = repo.findById(userId).orElseGet(() -> {
            UserSession s = new UserSession();
            s.setUserId(userId);
            return s;
        });
        session.setSessionToken(sessionToken);
        session.setIpAddress(ipAddress);
        session.setLastActiveAt(LocalDateTime.now());
        repo.save(session);
    }

    /**
     * Returns true if the given sessionToken matches the one registered for this user.
     * Returns true if no session record exists yet (graceful first-login tolerance).
     */
    public boolean isSessionValid(Long userId, String sessionToken) {
        Optional<UserSession> record = repo.findById(userId);
        if (record.isEmpty()) return true;
        return record.get().getSessionToken().equals(sessionToken);
    }

    public void updateLastActive(Long userId) {
        repo.findById(userId).ifPresent(s -> {
            s.setLastActiveAt(LocalDateTime.now());
            repo.save(s);
        });
    }

    @Transactional
    public void invalidate(Long userId) {
        repo.deleteById(userId);
    }
}
