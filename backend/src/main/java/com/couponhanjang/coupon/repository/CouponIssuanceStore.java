package com.couponhanjang.coupon.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** JPA 트랜잭션에 참여하는 PostgreSQL 원자적 조정 쿼리. */
@Repository
public class CouponIssuanceStore {
    private final JdbcTemplate jdbc;

    public CouponIssuanceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(UUID merchantId, UUID key, String fingerprint, Instant now) {
        return jdbc.update("""
                insert into idempotency_records(merchant_id, operation, request_key, fingerprint, created_at)
                values (?, 'ISSUE_COUPON', ?, ?, ?)
                on conflict (merchant_id, operation, request_key) do nothing
                """, merchantId, key, fingerprint, Timestamp.from(now)) == 1;
    }

    public record Previous(String fingerprint, UUID couponId) {
    }

    public Previous previous(UUID merchantId, UUID key) {
        return jdbc.queryForObject("""
                select fingerprint, result_id from idempotency_records
                where merchant_id = ? and operation = 'ISSUE_COUPON' and request_key = ?
                """, (rs, row) -> new Previous(rs.getString(1), rs.getObject(2, UUID.class)), merchantId, key);
    }

    public boolean reserve(UUID merchantId, LocalDate month, int limit) {
        return jdbc.update("""
                insert into monthly_message_usage(merchant_id, month_kst, reserved_count, settled_count)
                values (?, ?, 1, 0)
                on conflict (merchant_id, month_kst) do update
                set reserved_count = monthly_message_usage.reserved_count + 1
                where monthly_message_usage.reserved_count + monthly_message_usage.settled_count < ?
                """, merchantId, month, limit) == 1;
    }

    public UUID customer(UUID merchantId, String lookup, String ciphertext, Instant now) {
        return jdbc.queryForObject("""
                insert into customers(id, merchant_id, phone_lookup, phone_ciphertext, last_activity_at, created_at)
                values (?, ?, ?, ?, ?, ?)
                on conflict (merchant_id, phone_lookup) do update
                set last_activity_at = greatest(customers.last_activity_at, excluded.last_activity_at)
                returning id
                """, UUID.class, UUID.randomUUID(), merchantId, lookup, ciphertext,
                Timestamp.from(now), Timestamp.from(now));
    }

    public void enqueue(UUID merchantId, UUID couponId, String recipient, String payload, Instant now) {
        jdbc.update("""
                insert into message_outbox(id, merchant_id, coupon_id, kind, recipient_ciphertext,
                                           payload_ciphertext, state, created_at, key_version)
                values (?, ?, ?, 'COUPON_ISSUED', ?, ?, 'QUEUED', ?, 1)
                """, UUID.randomUUID(), merchantId, couponId, recipient, payload, Timestamp.from(now));
    }

    public void complete(UUID merchantId, UUID key, UUID couponId) {
        jdbc.update("""
                update idempotency_records set result_id = ?
                where merchant_id = ? and operation = 'ISSUE_COUPON' and request_key = ?
                """, couponId, merchantId, key);
    }
}
