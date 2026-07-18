package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One row per Claude API call — used to enforce the $20/month per-user spend cap
 *  (see TokenUsageService). Cost is computed from tokenIn/tokenOut at query time using each
 *  model's current per-token price, not stored here, since prices can change. */
@Entity
@Table(name = "token_usage")
public class TokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_session_id")
    private String userSessionId;

    @Column(name = "token_in", nullable = false)
    private long tokenIn;

    @Column(name = "token_out", nullable = false)
    private long tokenOut;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "search_type_id", nullable = false)
    private InputPayloadType searchTypeId;

    @Column(name = "date_created", nullable = false)
    private LocalDateTime dateCreated = LocalDateTime.now();

    public Long getId()                              { return id; }
    public Long getUserId()                          { return userId; }
    public void setUserId(Long v)                    { this.userId = v; }
    public String getUserSessionId()                 { return userSessionId; }
    public void setUserSessionId(String v)           { this.userSessionId = v; }
    public long getTokenIn()                         { return tokenIn; }
    public void setTokenIn(long v)                   { this.tokenIn = v; }
    public long getTokenOut()                        { return tokenOut; }
    public void setTokenOut(long v)                  { this.tokenOut = v; }
    public String getModelName()                     { return modelName; }
    public void setModelName(String v)               { this.modelName = v; }
    public InputPayloadType getSearchTypeId()        { return searchTypeId; }
    public void setSearchTypeId(InputPayloadType v)  { this.searchTypeId = v; }
    public LocalDateTime getDateCreated()            { return dateCreated; }
    public void setDateCreated(LocalDateTime v)      { this.dateCreated = v; }
}
