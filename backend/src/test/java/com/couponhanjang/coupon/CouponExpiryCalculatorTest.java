package com.couponhanjang.coupon;

import com.couponhanjang.coupon.service.CouponExpiryCalculator;
import com.couponhanjang.template.dto.ExpiryRule;
import com.couponhanjang.template.entity.ExpiryMode;
import com.couponhanjang.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class CouponExpiryCalculatorTest {
    CouponExpiryCalculator calculator = new CouponExpiryCalculator();

    @Test
    void relativeDaysIncludeIssuanceDateAtKoreanMidnight() {
        ExpiryRule rule = new ExpiryRule(ExpiryMode.RELATIVE_DAYS, null, null, 1);
        assertThat(calculator.resolve(rule, Instant.parse("2026-11-01T14:59:59Z")).expiresAt())
                .isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
        assertThat(calculator.resolve(rule, Instant.parse("2026-11-01T15:00:00Z")).expiresAt())
                .isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
    }

    @Test
    void fixedFutureStartAndExclusiveEndAreCalculatedInKoreanTime() {
        ExpiryRule rule = new ExpiryRule(ExpiryMode.FIXED_DATE, LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-03"), null);
        var value = calculator.resolve(rule, Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(value.startsAt()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
        assertThat(value.expiresAt()).isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
        assertThatThrownBy(() -> calculator.resolve(rule, Instant.parse("2026-11-03T15:00:00Z")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void unrepresentableDateRangesAreRejectedBeforeDatabaseWrites() {
        ExpiryRule rule = new ExpiryRule(ExpiryMode.RELATIVE_DAYS, null, null, Integer.MAX_VALUE);
        assertThatThrownBy(() -> calculator.resolve(rule, Instant.parse("2026-11-01T00:00:00Z")))
                .isInstanceOf(ApiException.class);
    }
}
