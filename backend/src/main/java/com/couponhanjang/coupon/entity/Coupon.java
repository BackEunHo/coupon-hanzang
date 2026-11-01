package com.couponhanjang.coupon.entity;

import com.couponhanjang.template.dto.TemplateValues;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupons")
public class Coupon {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID merchantId;
    @Column(nullable = false)
    private UUID templateId;
    private UUID customerId;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false, length = 500)
    private String benefit;
    @Column(nullable = false, length = 2000)
    private String notice;
    @Column(nullable = false)
    private Instant issuedAt;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant redeemedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;
    @Column(nullable = false, length = 20)
    private String issueMessageStatus;
    @Column(length = 64)
    private String codeMac;
    @Column(nullable = false)
    private int codeKeyVersion;
    @Column(nullable = false)
    private UUID consentOperatorId;
    @Column(nullable = false)
    private Instant consentCheckedAt;
    @Version
    @Column(nullable = false)
    private long rowVersion;

    protected Coupon() {
    }

    public Coupon(UUID id, UUID merchantId, UUID templateId, UUID customerId, TemplateValues values,
            Instant issuedAt, Instant startsAt, Instant expiresAt, UUID operatorId, String codeMac) {
        this.id = id;
        this.merchantId = merchantId;
        this.templateId = templateId;
        this.customerId = customerId;
        this.title = values.title();
        this.benefit = values.benefit();
        this.notice = values.notice();
        this.issuedAt = issuedAt;
        this.startsAt = startsAt;
        this.expiresAt = expiresAt;
        this.status = CouponStatus.PENDING_SEND;
        this.issueMessageStatus = "QUEUED";
        this.codeMac = codeMac;
        this.codeKeyVersion = 1;
        this.consentOperatorId = operatorId;
        this.consentCheckedAt = issuedAt;
    }

    public CouponStatus effectiveStatus(Instant now) {
        boolean expirable = status == CouponStatus.PENDING_SEND || status == CouponStatus.ACTIVE
                || status == CouponStatus.SEND_FAILED;
        return expirable && !now.isBefore(expiresAt) ? CouponStatus.EXPIRED : status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getTitle() {
        return title;
    }

    public String getBenefit() {
        return benefit;
    }

    public String getNotice() {
        return notice;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }

    public String getIssueMessageStatus() {
        return issueMessageStatus;
    }
}
