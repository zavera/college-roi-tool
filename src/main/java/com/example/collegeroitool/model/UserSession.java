package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_token", nullable = false, unique = true)
    private String sessionToken;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    public Long getUserId()                          { return userId; }
    public void setUserId(Long v)                    { this.userId = v; }
    public String getSessionToken()                  { return sessionToken; }
    public void setSessionToken(String v)            { this.sessionToken = v; }
    public String getIpAddress()                     { return ipAddress; }
    public void setIpAddress(String v)               { this.ipAddress = v; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
    public LocalDateTime getLastActiveAt()           { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime v)     { this.lastActiveAt = v; }
}
