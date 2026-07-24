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

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType = PlanType.MONTHLY;

    /** When the CURRENT plan/billing cycle started — reset on every plan switch (not the same as
     *  createdAt, which is when this row was first created). */
    @Column(name = "plan_start_date")
    private LocalDateTime planStartDate = LocalDateTime.now();

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
    public String getStripeCustomerId()        { return stripeCustomerId; }
    public void setStripeCustomerId(String v)  { this.stripeCustomerId = v; }
    public String getStripeSubscriptionId()     { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String v) { this.stripeSubscriptionId = v; }
    public PlanType getPlanType()              { return planType; }
    public void setPlanType(PlanType v)        { this.planType = v; }
    public LocalDateTime getPlanStartDate()    { return planStartDate; }
    public void setPlanStartDate(LocalDateTime v) { this.planStartDate = v; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
}
