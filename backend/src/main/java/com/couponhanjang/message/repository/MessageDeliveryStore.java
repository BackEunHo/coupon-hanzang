package com.couponhanjang.message.repository;

import com.couponhanjang.common.exception.ApiException;
import com.couponhanjang.integration.solapi.SolapiClient;
import com.couponhanjang.integration.solapi.SolapiProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Repository
public class MessageDeliveryStore {
    private final JdbcTemplate db;
    private final Clock clock;
    private final SolapiProperties settings;

    public MessageDeliveryStore(JdbcTemplate db, Clock clock, SolapiProperties settings) {
        this.db = db;
        this.clock = clock;
        this.settings = settings;
    }

    public record Dispatch(UUID id, UUID couponId, UUID token, String recipient, String payload) {
        @Override
        public String toString() {
            return "Dispatch[" + id + "]";
        }
    }

    public record Event(UUID id, String messageId, UUID attemptId, String code, String type, Instant reportedAt) {
    }

    public record PollTarget(UUID id, String messageId) {
    }

    public record UnidentifiedTarget(UUID id, Instant requestedAt) {
    }

    public record RetryResponse(UUID id, String kind, String status, Instant requestedAt) {
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    private boolean terminal(String state) {
        return Set.of("DELIVERED", "FAILED", "CANCELED").contains(state);
    }

    private Map<String, Object> lock(UUID id) {
        var reference = db.queryForList("select coupon_id from message_outbox where id=?", id);
        if (reference.isEmpty())
            return null;
        UUID coupon = (UUID) reference.get(0).get("coupon_id");
        db.queryForList("select id from coupons where id=? for update", coupon);
        return db.queryForMap("select * from message_outbox where id=? for update", id);
    }

    @Transactional
    public Optional<Dispatch> claimNext() {
        var candidates = db.queryForList("""
                select c.id from coupons c where exists (
                    select 1 from message_outbox o where o.coupon_id=c.id and o.id=c.current_outbox_id
                    and o.state='QUEUED' and o.dispatch_enabled and o.payload_version=2 and o.next_attempt_at<=?)
                order by c.issued_at for update skip locked limit 1
                """, UUID.class, now());
        if (candidates.isEmpty())
            return Optional.empty();
        UUID couponId = candidates.get(0);
        var row = db.queryForMap("""
                select o.* from message_outbox o join coupons c on c.current_outbox_id=o.id
                where c.id=? for update of o
                """, couponId);
        UUID id = (UUID) row.get("id");
        if (!eligible(couponId)) {
            db.update(
                    "update message_outbox set state='CANCELED',recipient_ciphertext=null,payload_ciphertext=null where id=?",
                    id);
            settle(row, false);
            updateCoupon(id, "FAILED", false, null);
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        db.update(
                "update message_outbox set state='PROCESSING',attempt_count=attempt_count+1,lease_token=?,lease_until=? where id=?",
                token, Timestamp.from(clock.instant().plusSeconds(60)), id);
        db.update("insert into message_deliveries(id,state,requested_at,next_check_at) values (?,'PROCESSING',?,?)", id,
                now(), now());
        return Optional.of(new Dispatch(id, couponId, token, (String) row.get("recipient_ciphertext"),
                (String) row.get("payload_ciphertext")));
    }

    private boolean eligible(UUID couponId) {
        return Boolean.TRUE.equals(db.queryForObject("""
                select c.status='PENDING_SEND' and c.expires_at>? and m.status='FREE_ACTIVE'
                    and cu.deleted_at is null and cu.phone_ciphertext is not null
                from coupons c join merchants m on m.id=c.merchant_id
                left join customers cu on cu.id=c.customer_id where c.id=?
                """, Boolean.class, now(), couponId));
    }

    @Transactional
    public void submission(Dispatch dispatch, SolapiClient.Submission result) {
        var row = lock(dispatch.id());
        if (row == null || !dispatch.token().equals(row.get("lease_token")) || terminal((String) row.get("state")))
            return;
        // 웹훅/조회로 먼저 확인된 접수는 뒤늦은 HTTP 타임아웃·거부로 되돌리지 않는다.
        if ("SUBMITTED".equals(row.get("state")))
            return;
        String state = switch (result.state()) {
            case ACCEPTED -> "SUBMITTED";
            case REJECTED -> "FAILED";
            case UNKNOWN -> "UNKNOWN";
        };
        db.update("update message_outbox set state=?,lease_until=null where id=?", state, dispatch.id());
        db.update(
                """
                        update message_deliveries set provider_message_id=coalesce(?,provider_message_id),provider_group_id=coalesce(?,provider_group_id),state=?,status_code=?,
                            actual_type=?,next_check_at=? where id=?
                        """,
                result.messageId(), result.groupId(), state, result.code(), safeType(result.type()),
                Timestamp.from(clock.instant().plusSeconds(30)), dispatch.id());
        if (state.equals("FAILED"))
            settle(row, false);
        updateCoupon(dispatch.id(), state, false, safeType(result.type()));
    }

    @Transactional
    public void cancel(Dispatch dispatch) {
        var row = lock(dispatch.id());
        if (row == null || !dispatch.token().equals(row.get("lease_token")) || !row.get("state").equals("PROCESSING"))
            return;
        db.update(
                "update message_outbox set state='CANCELED',lease_until=null,recipient_ciphertext=null,payload_ciphertext=null where id=?",
                dispatch.id());
        db.update("update message_deliveries set state='CANCELED' where id=?", dispatch.id());
        settle(row, false);
        updateCoupon(dispatch.id(), "FAILED", false, null);
    }

    @Transactional
    public void recoverExpiredLeases() {
        var ids = db.queryForList("select id from message_outbox where state='PROCESSING' and lease_until<? limit 50",
                UUID.class, now());
        for (UUID id : ids) {
            var row = lock(id);
            if (!row.get("state").equals("PROCESSING")
                    || ((Timestamp) row.get("lease_until")).toInstant().isAfter(clock.instant()))
                continue;
            db.update("update message_outbox set state='UNKNOWN',lease_until=null where id=?", id);
            db.update("update message_deliveries set state='UNKNOWN' where id=?", id);
            updateCoupon(id, "UNKNOWN", false, null);
        }
    }

    private void settle(Map<String, Object> row, boolean counted) {
        UUID id = (UUID) row.get("id");
        int changed = db.update(
                "update message_outbox set settlement_status=? where id=? and settlement_status='RESERVED'",
                counted ? "SETTLED" : "RELEASED", id);
        if (changed == 1) {
            int updated = db.update("""
                    update monthly_message_usage set reserved_count=reserved_count-1,settled_count=settled_count+?
                    where merchant_id=? and month_kst=? and reserved_count>0
                    """, counted ? 1 : 0, row.get("merchant_id"), row.get("reservation_month"));
            if (updated != 1)
                throw new IllegalStateException("Missing message quota reservation");
        }
    }

    private void updateCoupon(UUID outboxId, String messageState, boolean delivered, String type) {
        db.update(
                """
                        update coupons set issue_message_status=?, actual_message_type=coalesce(?,actual_message_type),
                          status=case when status in ('REDEEMED','EXPIRED','CLOSED_INACTIVE') then status
                                      when expires_at<=? then 'EXPIRED'
                                      when customer_id is null or not exists(select 1 from customers cu
                                          where cu.id=coupons.customer_id and cu.deleted_at is null and cu.phone_ciphertext is not null)
                                          then 'CLOSED_INACTIVE'
                                      when ? then 'ACTIVE' when ?='FAILED' then 'SEND_FAILED' else status end,
                          row_version=row_version+1
                        where current_outbox_id=?
                        """,
                messageState, type, now(), delivered, messageState, outboxId);
    }

    public static String safeType(String type) {
        return "SMS".equals(type) || "LMS".equals(type) ? type : null;
    }

    public static String outcome(String code) {
        if ("4000".equals(code))
            return "DELIVERED";
        if ("2000".equals(code) || "3000".equals(code))
            return "SUBMITTED";
        if (code != null && code.matches("[1235][0-9]{3}"))
            return "FAILED";
        return "UNKNOWN";
    }

    @Transactional
    public boolean apply(SolapiClient.ProviderResult result, boolean verifiedLookup) {
        var mapped = db.queryForList("select id from message_deliveries where provider_message_id=?", UUID.class,
                result.messageId());
        UUID id = mapped.isEmpty() ? result.attemptId() : mapped.get(0);
        if (id == null)
            return false;
        var row = lock(id);
        if (row == null)
            return false;
        if (mapped.isEmpty()) {
            if (!verifiedLookup || !Boolean.TRUE.equals(row.get("dispatch_enabled"))
                    || !Set.of("PROCESSING", "SUBMITTED", "UNKNOWN").contains(row.get("state")))
                return false;
            int bound = db.update(
                    "update message_deliveries set provider_message_id=?,provider_group_id=? where id=? and provider_message_id is null",
                    result.messageId(), result.groupId(), id);
            if (bound == 0)
                return false;
        } else if (result.attemptId() != null && !id.equals(result.attemptId()))
            return false;
        if (terminal((String) row.get("state")))
            return true;
        String state = outcome(result.code());
        if (state.equals("UNKNOWN"))
            return true;
        db.update(
                "update message_deliveries set state=?,status_code=?,actual_type=?,reported_at=?,next_check_at=? where id=?",
                state, result.code(), safeType(result.type()), Timestamp.from(result.reportedAt()),
                Timestamp.from(clock.instant().plusSeconds(60)), id);
        db.update("update message_outbox set state=?,lease_until=null where id=?", state, id);
        if (state.equals("DELIVERED") || state.equals("FAILED")) {
            boolean counted = state.equals("DELIVERED")
                    || (!result.code().startsWith("1") && settings.isCountDeliveryFailures());
            settle(row, counted);
            if (state.equals("DELIVERED"))
                db.update("update message_outbox set recipient_ciphertext=null,payload_ciphertext=null where id=?", id);
        }
        updateCoupon(id, state, state.equals("DELIVERED"), safeType(result.type()));
        return true;
    }

    @Transactional
    public void receive(List<Event> events) {
        for (Event event : events) {
            String key = digest(event.messageId() + ":" + event.attemptId() + ":" + event.code() + ":" + event.type()
                    + ":" + event.reportedAt());
            db.update(
                    """
                            insert into message_inbox(id,event_key,provider_message_id,attempt_id,status_code,actual_type,
                                reported_at,received_at,next_attempt_at) values (?,?,?,?,?,?,?,?,?) on conflict(event_key) do nothing
                            """,
                    event.id(), key, event.messageId(), event.attemptId(), event.code(), safeType(event.type()),
                    Timestamp.from(event.reportedAt()), now(), now());
        }
    }

    private String digest(String input) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public List<Event> pendingEvents() {
        return db.query("""
                select * from message_inbox where processed_at is null and next_attempt_at<=?
                order by received_at limit 50
                """, (rs, n) -> new Event(rs.getObject("id", UUID.class), rs.getString("provider_message_id"),
                rs.getObject("attempt_id", UUID.class), rs.getString("status_code"), rs.getString("actual_type"),
                rs.getTimestamp("reported_at").toInstant()), now());
    }

    public void finishEvent(UUID id, boolean handled) {
        db.update("update message_inbox set processed_at=?,next_attempt_at=? where id=?", handled ? now() : null,
                Timestamp.from(clock.instant().plusSeconds(60)), id);
    }

    public List<PollTarget> pollTargets() {
        return db.query("""
                select id,provider_message_id from message_deliveries where state in ('SUBMITTED','UNKNOWN')
                and provider_message_id is not null and next_check_at<=? order by next_check_at limit 10
                """, (rs, n) -> new PollTarget(rs.getObject(1, UUID.class), rs.getString(2)), now());
    }

    public List<UnidentifiedTarget> unidentifiedTargets() {
        return db.query("""
                select id,requested_at from message_deliveries where state='UNKNOWN'
                and provider_message_id is null and next_check_at<=? order by next_check_at limit 10
                """, (rs, n) -> new UnidentifiedTarget(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
                now());
    }

    public void polled(UUID id) {
        db.update("update message_deliveries set next_check_at=? where id=?",
                Timestamp.from(clock.instant().plusSeconds(60)), id);
    }

    @Transactional
    public RetryResponse retry(UUID merchantId, UUID couponId, UUID key,
            com.couponhanjang.coupon.repository.CouponIssuanceStore quota,
            com.couponhanjang.coupon.service.CustomerPhoneProtector phones, int limit) {
        var existingCoupon = db.queryForList("select id from coupons where id=? and merchant_id=?", couponId,
                merchantId);
        if (existingCoupon.isEmpty())
            throw new ApiException(404, "RESOURCE_NOT_FOUND", "Resource not found");
        int claimed = db.update("""
                insert into message_retry_requests(merchant_id,request_key,coupon_id,created_at) values (?,?,?,?)
                on conflict (merchant_id,request_key) do nothing
                """, merchantId, key, couponId, now());
        if (claimed == 0) {
            var previous = db.queryForMap("select * from message_retry_requests where merchant_id=? and request_key=?",
                    merchantId, key);
            if (!couponId.equals(previous.get("coupon_id")))
                throw new ApiException(409, "IDEMPOTENCY_CONFLICT", "Retry key was used for another coupon");
            return retryResponse((UUID) previous.get("outbox_id"));
        }
        var coupon = db.queryForMap("select * from coupons where id=? and merchant_id=? for update", couponId,
                merchantId);
        UUID previousId = (UUID) coupon.get("current_outbox_id");
        if (!"SEND_FAILED".equals(coupon.get("status")) || previousId == null
                || !((Timestamp) coupon.get("expires_at")).toInstant().isAfter(clock.instant()))
            throw cannotRetry();
        var previous = db.queryForMap("select * from message_outbox where id=? for update", previousId);
        if (!"FAILED".equals(previous.get("state")) || !Integer.valueOf(2).equals(previous.get("payload_version"))
                || previous.get("payload_ciphertext") == null || previous.get("recipient_ciphertext") == null)
            throw cannotRetry();
        boolean customerAvailable = Boolean.TRUE.equals(db.queryForObject(
                """
                        select exists(select 1 from customers cu join merchants m on m.id=cu.merchant_id
                        where cu.id=? and cu.merchant_id=? and cu.deleted_at is null and cu.phone_ciphertext is not null and m.status='FREE_ACTIVE')
                        """,
                Boolean.class, coupon.get("customer_id"), merchantId));
        if (!customerAvailable)
            throw cannotRetry();
        String phone = phones.decrypt((String) previous.get("recipient_ciphertext"));
        if (settings.isSendEnabled() && !settings.allows(phone))
            throw new ApiException(403, "RECIPIENT_NOT_ALLOWED", "Recipient is not allowed for test sending");
        LocalDate month = clock.instant().atZone(ZoneId.of("Asia/Seoul")).toLocalDate().withDayOfMonth(1);
        if (!quota.reserve(merchantId, month, limit))
            throw new ApiException(429, "QUOTA_EXCEEDED", "Monthly message quota exceeded");
        UUID id = UUID.randomUUID();
        db.update(
                """
                        insert into message_outbox(id,merchant_id,coupon_id,kind,recipient_ciphertext,payload_ciphertext,state,
                            created_at,key_version,payload_version,dispatch_enabled,expected_type,byte_length,reservation_month,next_attempt_at)
                        values (?,?,?,'COUPON_ISSUED',?,?,'QUEUED',?,1,2,?,?,?,?,?)
                        """,
                id, merchantId, couponId, previous.get("recipient_ciphertext"), previous.get("payload_ciphertext"),
                now(),
                settings.isSendEnabled(), previous.get("expected_type"), previous.get("byte_length"), month, now());
        db.update(
                "update coupons set current_outbox_id=?,status='PENDING_SEND',issue_message_status='QUEUED',actual_message_type=null,row_version=row_version+1 where id=?",
                id, couponId);
        db.update("update message_retry_requests set outbox_id=? where merchant_id=? and request_key=?", id, merchantId,
                key);
        return retryResponse(id);
    }

    private RetryResponse retryResponse(UUID id) {
        return db.queryForObject("select id,kind,state,created_at from message_outbox where id=?",
                (rs, n) -> new RetryResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant()),
                id);
    }

    private ApiException cannotRetry() {
        return new ApiException(409, "DELIVERY_NOT_RETRYABLE",
                "Only a confirmed failed, unexpired delivery can be retried");
    }

}
