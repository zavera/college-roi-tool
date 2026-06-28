package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    public Long getId()                    { return id; }
    public String getToken()               { return token; }
    public void   setToken(String t)       { this.token = t; }
    public String getEmail()               { return email; }
    public void   setEmail(String e)       { this.email = e; }
    public LocalDateTime getExpiresAt()    { return expiresAt; }
    public void   setExpiresAt(LocalDateTime d) { this.expiresAt = d; }
    public boolean isUsed()                { return used; }
    public void    setUsed(boolean u)      { this.used = u; }
}
