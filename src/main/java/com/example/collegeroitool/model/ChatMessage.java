package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_session_id", nullable = false)
    private Long chatSessionId;

    // Redundant with chat_session.user_id, but kept directly on the row so every query that
    // reads a user's own chat history can scope by user_id alone — never trusting a client-
    // supplied chatSessionId without also checking it belongs to this user.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** "user" or "assistant". */
    @Column(name = "role", nullable = false)
    private String role;

    // No @Lob: see Fafsa.java — @Lob on a String forces the Postgres Large Object API, which
    // requires a non-autocommit connection.
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId()                          { return id; }
    public Long getChatSessionId()               { return chatSessionId; }
    public void setChatSessionId(Long v)         { this.chatSessionId = v; }
    public Long getUserId()                      { return userId; }
    public void setUserId(Long v)                { this.userId = v; }
    public String getRole()                      { return role; }
    public void   setRole(String v)               { this.role = v; }
    public String getContent()                   { return content; }
    public void   setContent(String v)            { this.content = v; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
}
