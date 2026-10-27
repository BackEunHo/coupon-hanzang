package com.couponhanjang.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operators")
public class Operator {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;
    @Column(length = 512)
    private String phoneCiphertext;
    @Column(length = 64, unique = true)
    private String phoneLookup;
    @Column(length = 255)
    private String passwordHash;
    private Instant phoneVerifiedAt;
    @Column(nullable = false)
    private long credentialVersion;
    @Column(nullable = false)
    private int failedLoginAttempts;
    private Instant loginLockedUntil;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant withdrawnAt;
    @Version
    @Column(nullable = false)
    private long rowVersion;

    protected Operator() {
    }

    public Operator(Merchant merchant, String phoneCiphertext, String phoneLookup, String passwordHash, Instant now) {
        this.id = UUID.randomUUID();
        this.merchant = merchant;
        this.phoneCiphertext = phoneCiphertext;
        this.phoneLookup = phoneLookup;
        this.passwordHash = passwordHash;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    public boolean isActive() {
        return withdrawnAt == null && merchant.getStatus() == Merchant.Status.FREE_ACTIVE;
    }

    public boolean isLoginLocked(Instant now) {
        return loginLockedUntil != null && now.isBefore(loginLockedUntil);
    }

    public void recordFailedLogin(Instant now) {
        if (loginLockedUntil != null && !now.isBefore(loginLockedUntil)) {
            failedLoginAttempts = 0;
            loginLockedUntil = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= 5)
            loginLockedUntil = now.plusSeconds(15 * 60);
    }

    public void clearFailedLogins() {
        failedLoginAttempts = 0;
        loginLockedUntil = null;
    }

    public void changePassword(String hash) {
        passwordHash = hash;
        credentialVersion++;
    }

    public void withdraw(Instant now) {
        phoneCiphertext = null;
        phoneLookup = null;
        passwordHash = null;
        credentialVersion++;
        withdrawnAt = now;
        merchant.withdraw(now);
    }
}
