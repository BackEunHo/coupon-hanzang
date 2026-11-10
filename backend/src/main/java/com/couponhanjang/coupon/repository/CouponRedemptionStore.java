package com.couponhanjang.coupon.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Participates in the service transaction. Lock order: coupon, request key,
 * customer.
 */
@Repository
public class CouponRedemptionStore {
    private final JdbcTemplate db;

    public CouponRedemptionStore(JdbcTemplate db) {
        this.db = db;
    }

    public record RedemptionRecord(UUID couponId, String fingerprint, Integer httpStatus, String errorCode) {
    }

    public boolean claim(UUID merchantId, UUID couponId, UUID key, String fingerprint, Instant now) {
        return db.update("""
                insert into coupon_redemption_requests(merchant_id,request_key,coupon_id,fingerprint,created_at)
                values (?,?,?,?,?) on conflict (merchant_id,request_key) do nothing
                """, merchantId, key, couponId, fingerprint, Timestamp.from(now)) == 1;
    }

    public RedemptionRecord previous(UUID merchantId, UUID key) {
        return db.queryForObject("""
                select coupon_id,fingerprint,http_status,error_code from coupon_redemption_requests
                where merchant_id=? and request_key=?
                """, (rs, n) -> new RedemptionRecord(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, Integer.class), rs.getString(4)), merchantId, key);
    }

    public void complete(UUID merchantId, UUID key, int httpStatus, String errorCode) {
        db.update("""
                update coupon_redemption_requests set http_status=?,error_code=?
                where merchant_id=? and request_key=?
                """, httpStatus, errorCode, merchantId, key);
    }

    public boolean lockAvailableCustomer(UUID merchantId, UUID customerId) {
        if (customerId == null)
            return false;
        return !db.queryForList("""
                select id from customers where id=? and merchant_id=?
                and deleted_at is null and phone_ciphertext is not null for update
                """, UUID.class, customerId, merchantId).isEmpty();
    }

    public void touchCustomer(UUID merchantId, UUID customerId, Instant now) {
        db.update("""
                update customers set last_activity_at=greatest(last_activity_at,?)
                where id=? and merchant_id=?
                """, Timestamp.from(now), customerId, merchantId);
    }
}
