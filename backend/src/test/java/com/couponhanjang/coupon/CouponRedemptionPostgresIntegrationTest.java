package com.couponhanjang.coupon;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.quota.monthly-message-limit=100")
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "postgres-test" })
@Testcontainers
@Import(CouponRedemptionPostgresIntegrationTest.FixedTime.class)
class CouponRedemptionPostgresIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock fixedTestClock() {
            return new TestClock();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate db;
    final String origin = "http://localhost:8080";
    final String templateBody = """
            {"title":"첫 쿠폰","benefit":"1000원 할인","notice":"사용 안내","expiry":{"mode":"RELATIVE_DAYS","durationDays":14}}
            """;

    Cookie signup() throws Exception {
        String phone = "010" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").concat("12345678").substring(0, 8);
        return mvc.perform(post("/api/v1/auth/signup").header("Origin", origin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"merchantName\":\"상점\",\"phone\":\"" + phone + "\",\"password\":\"password-123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    String template(Cookie owner) throws Exception {
        return json.readTree(mvc
                .perform(post("/api/v1/templates").cookie(owner).header("Origin", origin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    String body(String id) {
        return "{\"templateId\":\"" + id + "\",\"recipientPhone\":\"010-1234-5678\",\"consentConfirmed\":true}";
    }

    MvcResult issue(Cookie owner, String id, String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/coupons").cookie(owner).header("Origin", origin).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    static class TestClock extends Clock {
        volatile Instant time = Instant.parse("2026-11-01T14:59:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return time;
        }
    }

    @Autowired
    Clock clock;
    @Autowired
    com.couponhanjang.message.repository.MessageDeliveryStore deliveries;
    @org.springframework.beans.factory.annotation.Value("${app.coupon-code.encryption-key}")
    String encryption;
    @org.springframework.beans.factory.annotation.Value("${app.coupon-code.mac-key}")
    String mac;

    @org.junit.jupiter.api.AfterEach
    void resetTime() {
        ((TestClock) clock).time = Instant.parse("2026-11-01T14:59:00Z");
    }

    final String code = "012345";

    UUID active(Cookie owner) throws Exception {
        String t = template(owner);
        var result = issue(owner, t, UUID.randomUUID().toString(), body(t));
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
        String hash = new com.couponhanjang.common.crypto.SecretCipher(encryption, mac).mac(id + ":" + code);
        db.update("update coupons set status='ACTIVE',issue_message_status='DELIVERED',code_mac=? where id=?", hash,
                id);
        db.update("update customers set last_activity_at=? where id=(select customer_id from coupons where id=?)",
                java.sql.Timestamp.from(clock.instant().minusSeconds(60)), id);
        return id;
    }

    MvcResult redeem(Cookie owner, UUID id, String key, String code) throws Exception {
        return mvc.perform(post("/api/v1/coupons/" + id + "/redeem").cookie(owner).header("Origin", origin)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", code)))).andReturn();
    }

    void expect(MvcResult r, int status, String error) throws Exception {
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(status);
        if (error != null)
            assertThat(json.readTree(r.getResponse().getContentAsString()).get("code").asText()).isEqualTo(error);
    }

    @Test
    void successReplayAndDistinctRequestCannotRedeemTwiceOrSendMoreMessages() throws Exception {
        Cookie owner = signup();
        UUID id = active(owner);
        String key = UUID.randomUUID().toString();
        var usage = db.queryForMap(
                "select * from monthly_message_usage where merchant_id=(select merchant_id from coupons where id=?)",
                id);
        expect(redeem(owner, id, key, code), 200, null);
        var row = db.queryForMap("select * from coupons where id=?", id);
        assertThat(row.get("status")).isEqualTo("REDEEMED");
        assertThat(row.get("code_mac")).isNull();
        assertThat(((java.sql.Timestamp) row.get("redeemed_at")).toInstant()).isEqualTo(clock.instant());
        var activity = db.queryForObject(
                "select last_activity_at from customers where id=(select customer_id from coupons where id=?)",
                java.sql.Timestamp.class, id);
        ((TestClock) clock).time = clock.instant().plusSeconds(1);
        expect(redeem(owner, id, key, code), 200, null);
        expect(redeem(owner, id, UUID.randomUUID().toString(), code), 409, "COUPON_NOT_REDEEMABLE");
        assertThat(db.queryForObject(
                "select last_activity_at from customers where id=(select customer_id from coupons where id=?)",
                java.sql.Timestamp.class, id)).isEqualTo(activity);
        assertThat(db.queryForObject("select redeemed_at from coupons where id=?", java.sql.Timestamp.class, id))
                .isEqualTo(row.get("redeemed_at"));
        assertThat(db.queryForObject("select count(*) from message_outbox where coupon_id=?", Integer.class, id))
                .isEqualTo(1);
        assertThat(db.queryForMap(
                "select * from monthly_message_usage where merchant_id=(select merchant_id from coupons where id=?)",
                id)).isEqualTo(usage);
        mvc.perform(get("/api/v1/coupons/" + id).cookie(owner)).andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.code").doesNotExist()).andExpect(jsonPath("$.codeMac").doesNotExist());
        mvc.perform(get("/api/v1/coupons?status=ACTIVE").cookie(owner)).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void tenantAuthenticationAndStrictBodyAreEnforced() throws Exception {
        Cookie owner = signup(), other = signup();
        UUID id = active(owner);
        expect(redeem(other, id, UUID.randomUUID().toString(), code), 404, "RESOURCE_NOT_FOUND");
        expect(redeem(owner, UUID.randomUUID(), UUID.randomUUID().toString(), code), 404, "RESOURCE_NOT_FOUND");
        expect(redeem(owner, id, "bad-key", code), 400, "INVALID_REQUEST");
        for (String value : List.of("12345", "1234567", " 12345", "１２３４５６"))
            expect(redeem(owner, id, UUID.randomUUID().toString(), value), 400, "INVALID_REQUEST");
        mvc.perform(post("/api/v1/coupons/" + id + "/redeem").header("Origin", origin))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/coupons/" + id + "/redeem").cookie(owner)).andExpect(status().isForbidden());
        for (String body : List.of("{\"code\":123456}", "{\"code\":\"012345\",\"merchantId\":\"x\"}"))
            mvc.perform(post("/api/v1/coupons/" + id + "/redeem").cookie(owner).header("Origin", origin)
                    .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void failedCodesCommitAndReplayDoesNotCountAgainAndCooldownExpires() throws Exception {
        Cookie owner = signup();
        UUID id = active(owner);
        String firstKey = UUID.randomUUID().toString();
        expect(redeem(owner, id, firstKey, "999999"), 422, "COUPON_CODE_INVALID");
        expect(redeem(owner, id, firstKey, "999999"), 422, "COUPON_CODE_INVALID");
        assertThat(db.queryForObject("select redeem_failed_attempts from coupons where id=?", Integer.class, id))
                .isEqualTo(1);
        expect(redeem(owner, id, firstKey, code), 409, "IDEMPOTENCY_CONFLICT");
        for (int i = 2; i <= 4; i++)
            expect(redeem(owner, id, UUID.randomUUID().toString(), "999999"), 422, "COUPON_CODE_INVALID");
        expect(redeem(owner, id, UUID.randomUUID().toString(), "999999"), 429, "COUPON_CODE_RATE_LIMITED");
        expect(redeem(owner, id, UUID.randomUUID().toString(), code), 429, "COUPON_CODE_RATE_LIMITED");
        ((TestClock) clock).time = clock.instant().plusSeconds(900);
        expect(redeem(owner, id, UUID.randomUUID().toString(), code), 200, null);
    }

    @Test
    void statesMissingCustomerAndKeyVersionsCannotBeUsed() throws Exception {
        Cookie owner = signup();
        UUID id = active(owner);
        for (String state : List.of("PENDING_SEND", "SEND_FAILED", "REDEEMED", "EXPIRED", "CLOSED_INACTIVE")) {
            db.update("update coupons set status=? where id=?", state, id);
            expect(redeem(owner, id, UUID.randomUUID().toString(), code), 409, "COUPON_NOT_REDEEMABLE");
        }
        db.update("update coupons set status='ACTIVE',issue_message_status='SUBMITTED' where id=?", id);
        expect(redeem(owner, id, UUID.randomUUID().toString(), code), 409, "COUPON_NOT_REDEEMABLE");
        db.update("update coupons set issue_message_status='DELIVERED',code_key_version=2 where id=?", id);
        expect(redeem(owner, id, UUID.randomUUID().toString(), code), 409, "COUPON_NOT_REDEEMABLE");
        db.update("update coupons set code_key_version=1 where id=?", id);
        db.update(
                "update customers set deleted_at=current_timestamp,phone_ciphertext=null where id=(select customer_id from coupons where id=?)",
                id);
        expect(redeem(owner, id, UUID.randomUUID().toString(), code), 409, "COUPON_NOT_REDEEMABLE");
        db.update("update coupons set customer_id=null where id=?", id);
        expect(redeem(owner, id, UUID.randomUUID().toString(), code), 409, "COUPON_NOT_REDEEMABLE");
    }

    @Test
    void exactValidityBoundariesAreCheckedAfterLocking() throws Exception {
        Cookie owner = signup();
        UUID id = active(owner);
        Instant start = clock.instant();
        db.update("update coupons set starts_at=?,expires_at=? where id=?",
                java.sql.Timestamp.from(start.plusSeconds(1)), java.sql.Timestamp.from(start.plusSeconds(2)), id);
        expect(redeem(owner, id, UUID.randomUUID().toString(), code), 409, "COUPON_NOT_REDEEMABLE");
        ((TestClock) clock).time = start.plusSeconds(1);
        expect(redeem(owner, id, UUID.randomUUID().toString(), code), 200, null);
        UUID second = active(owner);
        db.update("update coupons set expires_at=? where id=?", java.sql.Timestamp.from(clock.instant().plusSeconds(1)),
                second);
        ((TestClock) clock).time = clock.instant().plusSeconds(1);
        expect(redeem(owner, second, UUID.randomUUID().toString(), code), 409, "COUPON_NOT_REDEEMABLE");
    }

    @Test
    void concurrentRequestsHaveOnlyOneTransitionAndSameKeyReplays() throws Exception {
        Cookie owner = signup();
        UUID id = active(owner);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> redeem(owner, id, UUID.randomUUID().toString(), code));
            var b = pool.submit(() -> redeem(owner, id, UUID.randomUUID().toString(), code));
            assertThat(List.of(a.get(20, TimeUnit.SECONDS).getResponse().getStatus(),
                    b.get(20, TimeUnit.SECONDS).getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
            UUID next = active(owner);
            String key = UUID.randomUUID().toString();
            var c = pool.submit(() -> redeem(owner, next, key, code));
            var d = pool.submit(() -> redeem(owner, next, key, code));
            expect(c.get(20, TimeUnit.SECONDS), 200, null);
            expect(d.get(20, TimeUnit.SECONDS), 200, null);
            assertThat(db.queryForObject("select count(*) from coupon_redemption_requests where coupon_id=?",
                    Integer.class, next)).isEqualTo(1);
            expect(redeem(owner, id, key, code), 409, "IDEMPOTENCY_CONFLICT");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void persistenceFailureRollsBackCouponActivityAndRequest() throws Exception {
        Cookie owner = signup();
        UUID id = active(owner);
        String key = UUID.randomUUID().toString();
        var before = db.queryForObject(
                "select last_activity_at from customers where id=(select customer_id from coupons where id=?)",
                java.sql.Timestamp.class, id);
        db.execute(
                "alter table coupon_redemption_requests add constraint test_redemption_failure check (request_key <> '"
                        + key + "'::uuid or http_status is null) not valid");
        try {
            expect(redeem(owner, id, key, code), 409, "DATA_CONFLICT");
        } finally {
            db.execute("alter table coupon_redemption_requests drop constraint test_redemption_failure");
        }
        assertThat(db.queryForObject("select status from coupons where id=?", String.class, id)).isEqualTo("ACTIVE");
        assertThat(db.queryForObject(
                "select last_activity_at from customers where id=(select customer_id from coupons where id=?)",
                java.sql.Timestamp.class, id)).isEqualTo(before);
        assertThat(db.queryForObject("select count(*) from coupon_redemption_requests where request_key=?",
                Integer.class, UUID.fromString(key))).isZero();
        expect(redeem(owner, id, key, code), 200, null);
    }

    @Autowired
    javax.sql.DataSource dataSource;

    @Test
    void expiryWhileWaitingForCouponLockIsRejected() throws Exception {
        Cookie owner = signup();
        UUID id = active(owner);
        db.update("update coupons set expires_at=? where id=?", java.sql.Timestamp.from(clock.instant().plusSeconds(2)),
                id);
        var pool = Executors.newSingleThreadExecutor();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select id from coupons where id=? for update")) {
                statement.setObject(1, id);
                statement.executeQuery().close();
            }
            var waiting = pool.submit(() -> redeem(owner, id, UUID.randomUUID().toString(), code));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                    .until(() -> Boolean.TRUE.equals(db.queryForObject(
                            "select exists(select 1 from pg_stat_activity where wait_event_type='Lock' and query like '%coupons%')",
                            Boolean.class)));
            ((TestClock) clock).time = clock.instant().plusSeconds(2);
            connection.commit();
            expect(waiting.get(15, TimeUnit.SECONDS), 409, "COUPON_NOT_REDEEMABLE");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void customerDeletionWhileWaitingForCustomerLockIsRejected() throws Exception {
        Cookie owner = signup();
        UUID id = active(owner);
        UUID customer = db.queryForObject("select customer_id from coupons where id=?", UUID.class, id);
        var pool = Executors.newSingleThreadExecutor();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "update customers set phone_ciphertext=null,deleted_at=current_timestamp where id=?")) {
                statement.setObject(1, customer);
                statement.executeUpdate();
            }
            var waiting = pool.submit(() -> redeem(owner, id, UUID.randomUUID().toString(), code));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                    .until(() -> Boolean.TRUE.equals(db.queryForObject(
                            "select exists(select 1 from pg_stat_activity where wait_event_type='Lock' and query like '%customers%')",
                            Boolean.class)));
            connection.commit();
            expect(waiting.get(15, TimeUnit.SECONDS), 409, "COUPON_NOT_REDEEMABLE");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void delayedDeliveryCannotReopenRedeemedCouponAndKeysAreMerchantScoped() throws Exception {
        Cookie owner = signup(), other = signup();
        UUID id = active(owner), second = active(other);
        String key = UUID.randomUUID().toString();
        expect(redeem(owner, id, key, code), 200, null);
        expect(redeem(other, second, key, code), 200, null);
        UUID outbox = db.queryForObject("select current_outbox_id from coupons where id=?", UUID.class, id);
        db.update(
                "insert into message_deliveries(id,provider_message_id,state,requested_at) values (?,?,'SUBMITTED',?)",
                outbox, "late-redemption", java.sql.Timestamp.from(clock.instant()));
        var redeemed = db.queryForObject("select redeemed_at from coupons where id=?", java.sql.Timestamp.class, id);
        assertThat(deliveries.apply(new com.couponhanjang.integration.solapi.SolapiClient.ProviderResult(
                "late-redemption", null, "4000", "LMS", outbox, clock.instant()), false)).isTrue();
        assertThat(db.queryForObject("select status from coupons where id=?", String.class, id)).isEqualTo("REDEEMED");
        assertThat(db.queryForObject("select redeemed_at from coupons where id=?", java.sql.Timestamp.class, id))
                .isEqualTo(redeemed);
        assertThat(db.queryForObject("select fingerprint from coupon_redemption_requests where coupon_id=?",
                String.class, id)).matches("[0-9a-f]{64}");
    }

}
