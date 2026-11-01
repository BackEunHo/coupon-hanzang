package com.couponhanjang.coupon.dto;

import com.couponhanjang.coupon.entity.*;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(UUID id, UUID templateId, String title, String benefit, String notice, String displayPhone,
        boolean customerDataDeleted, Instant issuedAt, Instant startsAt, Instant expiresAt, Instant redeemedAt,
        CouponStatus status, String issueMessageStatus, String closureReason) {
}
