package com.couponhanjang.coupon.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers", uniqueConstraints = @UniqueConstraint(columnNames = { "merchant_id", "phone_lookup" }))
public class Customer {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID merchantId;
    @Column(length = 512)
    private String phoneCiphertext;
    @Column(length = 64)
    private String phoneLookup;
    @Column(nullable = false)
    private Instant lastActivityAt;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant deletedAt;

    protected Customer() {
    }

    public String displayPhone(com.couponhanjang.coupon.service.CustomerPhoneProtector protector) {
        return deletedAt != null || phoneCiphertext == null ? "010-****-****" : protector.decrypt(phoneCiphertext);
    }

    public boolean isDeleted() {
        return deletedAt != null || phoneCiphertext == null;
    }
}
