package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    private boolean active = false;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId()                        { return id; }
    public Long getUserId()                    { return userId; }
    public void setUserId(Long v)              { this.userId = v; }
    public boolean isActive()                  { return active; }
    public void    setActive(boolean v)        { this.active = v; }
    public int  getAmountCents()               { return amountCents; }
    public void setAmountCents(int v)          { this.amountCents = v; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
}
