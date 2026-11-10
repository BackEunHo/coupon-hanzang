package com.couponhanjang.coupon.service;

import com.couponhanjang.common.api.JsonInput;
import com.couponhanjang.common.exception.ApiException;
import com.couponhanjang.coupon.dto.CouponResponse;
import com.couponhanjang.coupon.repository.CouponRedemptionStore;
import com.couponhanjang.coupon.repository.CouponRepository;
import com.couponhanjang.template.service.CouponTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class CouponRedemptionService {
    private final CouponRepository coupons;
    private final CouponRedemptionStore requests;
    private final CouponCodeProtector codes;
    private final CouponQueryService query;
    private final Clock clock;

    public CouponRedemptionService(CouponRepository coupons, CouponRedemptionStore requests,
            CouponCodeProtector codes, CouponQueryService query, Clock clock) {
        this.coupons = coupons;
        this.requests = requests;
        this.codes = codes;
        this.query = query;
        this.clock = clock;
    }

    // Business errors retain failed attempts and completed request results;
    // unexpected errors roll back.
    @Transactional(noRollbackFor = ApiException.class)
    public CouponResponse redeem(UUID merchantId, UUID couponId, UUID requestKey, String code) {
        if (code == null || !code.matches("[0-9]{6}"))
            throw JsonInput.invalid();
        var coupon = coupons.findForRedemption(merchantId, couponId)
                .orElseThrow(CouponTemplateService::notFound);
        String fingerprint = codes.redemptionFingerprint(couponId, code);
        if (!requests.claim(merchantId, couponId, requestKey, fingerprint, clock.instant())) {
            var previous = requests.previous(merchantId, requestKey);
            if (!couponId.equals(previous.couponId()) || !fingerprint.equals(previous.fingerprint())) {
                throw new ApiException(409, "IDEMPOTENCY_CONFLICT", "Request key was used for different contents");
            }
            if (previous.httpStatus() == null)
                throw new IllegalStateException("Incomplete redemption record");
            if (previous.httpStatus() != 200)
                throw businessError(previous.httpStatus(), previous.errorCode());
            return query.response(coupon);
        }

        if (!coupon.canRedeem(clock.instant()) || !requests.lockAvailableCustomer(merchantId, coupon.getCustomerId())) {
            throw fail(merchantId, requestKey, 409, "COUPON_NOT_REDEEMABLE");
        }
        // Time may have advanced while waiting for the customer row.
        var now = clock.instant();
        if (!coupon.canRedeem(now))
            throw fail(merchantId, requestKey, 409, "COUPON_NOT_REDEEMABLE");
        if (coupon.isRedemptionLocked(now))
            throw fail(merchantId, requestKey, 429, "COUPON_CODE_RATE_LIMITED");
        if (!codes.verify(couponId, code, coupon.getCodeMac())) {
            coupon.recordInvalidRedemption(now);
            throw fail(merchantId, requestKey, coupon.isRedemptionLocked(now) ? 429 : 422,
                    coupon.isRedemptionLocked(now) ? "COUPON_CODE_RATE_LIMITED" : "COUPON_CODE_INVALID");
        }
        coupon.redeem(now);
        requests.touchCustomer(merchantId, coupon.getCustomerId(), now);
        requests.complete(merchantId, requestKey, 200, null);
        return query.response(coupon);
    }

    private ApiException fail(UUID merchantId, UUID key, int status, String code) {
        requests.complete(merchantId, key, status, code);
        return businessError(status, code);
    }

    private ApiException businessError(int status, String code) {
        String message = switch (code) {
            case "COUPON_CODE_INVALID" -> "Coupon code is incorrect";
            case "COUPON_CODE_RATE_LIMITED" -> "Coupon code attempts are temporarily limited";
            default -> "Coupon cannot be redeemed";
        };
        return new ApiException(status, code, message);
    }
}
