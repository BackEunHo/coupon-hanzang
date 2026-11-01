package com.couponhanjang.coupon.service;

import com.couponhanjang.common.exception.ApiException;
import com.couponhanjang.template.dto.ExpiryRule;
import com.couponhanjang.template.entity.ExpiryMode;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class CouponExpiryCalculator {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public record ResolvedExpiry(Instant startsAt, Instant expiresAt) {
    }

    public ResolvedExpiry resolve(ExpiryRule rule, Instant issuedAt) {
        try {
            LocalDate today = issuedAt.atZone(ZONE).toLocalDate();
            if (rule.mode() == ExpiryMode.RELATIVE_DAYS) {
                LocalDate end = today.plusDays(rule.durationDays());
                if (end.getYear() > 9999) {
                    throw invalid();
                }
                return new ResolvedExpiry(issuedAt, end.atStartOfDay(ZONE).toInstant());
            }
            if (rule.date().equals(LocalDate.of(9999, 12, 31))) {
                throw invalid();
            }
            Instant starts = rule.startDate().atStartOfDay(ZONE).toInstant();
            Instant expires = rule.date().plusDays(1).atStartOfDay(ZONE).toInstant();
            if (!expires.isAfter(issuedAt)) {
                throw invalid();
            }
            return new ResolvedExpiry(starts.isAfter(issuedAt) ? starts : issuedAt, expires);
        } catch (DateTimeException | ArithmeticException error) {
            throw invalid();
        }
    }

    private ApiException invalid() {
        return new ApiException(422, "INVALID_EXPIRY", "Coupon validity has expired or is invalid");
    }
}
