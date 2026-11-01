package com.couponhanjang.template.entity;

import com.couponhanjang.template.dto.ExpiryRule;
import com.couponhanjang.template.dto.TemplateValues;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "coupon_templates")
public class CouponTemplate {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID merchantId;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false, length = 500)
    private String benefit;
    @Column(nullable = false, length = 2000)
    private String notice;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpiryMode expiryMode;
    private LocalDate fixedStartDate;
    private LocalDate fixedEndDate;
    private Integer relativeDurationDays;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant deletedAt;

    protected CouponTemplate() {
    }

    public CouponTemplate(UUID merchantId, TemplateValues values, Instant now) {
        this.id = UUID.randomUUID();
        this.merchantId = merchantId;
        this.createdAt = now;
        update(values, now);
    }

    public void update(TemplateValues values, Instant now) {
        title = values.title();
        benefit = values.benefit();
        notice = values.notice();
        expiryMode = values.expiry().mode();
        fixedStartDate = values.expiry().startDate();
        fixedEndDate = values.expiry().date();
        relativeDurationDays = values.expiry().durationDays();
        updatedAt = now;
    }

    public void delete(Instant now) {
        deletedAt = now;
        updatedAt = now;
    }

    public TemplateValues values() {
        return new TemplateValues(title, benefit, notice,
                new ExpiryRule(expiryMode, fixedStartDate, fixedEndDate, relativeDurationDays));
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
