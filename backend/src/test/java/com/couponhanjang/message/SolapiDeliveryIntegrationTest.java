package com.couponhanjang.message;

import com.fasterxml.jackson.databind.*;
import com.couponhanjang.integration.solapi.*;
import com.couponhanjang.message.service.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.AfterEach;
import static org.mockito.Mockito.*;
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

@SpringBootTest(properties = { "app.quota.monthly-message-limit=100", "solapi.webhook-secret=test-webhook",
        "solapi.background-enabled=false" })
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "postgres-test" })
@Testcontainers
@Import(SolapiDeliveryIntegrationTest.FixedTime.class)
class SolapiDeliveryIntegrationTest {
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
            return Clock.fixed(Instant.parse("2026-11-01T14:59:00Z"), ZoneOffset.UTC);
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

    @MockitoBean
    SolapiClient provider;
    @Autowired
    SolapiProperties settings;
    @Autowired
    MessageDispatchWorker worker;
    @Autowired
    DeliveryResultProcessor results;

    @AfterEach
    void disableSending() {
        settings.setSendEnabled(false);
    }

    void live() {
        settings.setApiKey("test-key");
        settings.setApiSecret("test-secret");
        settings.setFromNumber("01011112222");
        settings.setAllowedRecipients(Set.of("01012345678"));
        settings.setSendEnabled(true);
    }

    JsonNode issued(Cookie owner, String template) throws Exception {
        var r = issue(owner, template, UUID.randomUUID().toString(), body(template));
        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        return json.readTree(r.getResponse().getContentAsString());
    }

    UUID outbox(JsonNode coupon) {
        return db.queryForObject("select current_outbox_id from coupons where id=?", UUID.class,
                UUID.fromString(coupon.get("id").asText()));
    }

    void event(String messageId, UUID attempt, String code) throws Exception {
        String payload = "[{\"messageId\":\"" + messageId + "\",\"groupId\":\"group1\",\"statusCode\":\"" + code
                + "\",\"type\":\"LMS\",\"dateReported\":\"2026-11-01T15:00:00Z\",\"customFields\":{\"attemptId\":\""
                + attempt + "\"},\"to\":\"01012345678\"}]";
        mvc.perform(post("/api/v1/webhooks/solapi").header("X-Solapi-Secret", sha1())
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
    }

    String sha1() throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1")
                .digest("test-webhook".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void previewMasksCodeAndInvalidIssueWritesNothing() throws Exception {
        Cookie owner = signup(), other = signup();
        String t = template(owner);
        mvc.perform(post("/api/v1/coupons/preview").cookie(owner).header("Origin", origin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"" + t + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sendable").value(true))
                .andExpect(jsonPath("$.expectedMessageType").value("LMS"))
                .andExpect(jsonPath("$.previewText").value(org.hamcrest.Matchers.containsString("******")));
        mvc.perform(post("/api/v1/coupons/preview").cookie(other).header("Origin", origin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"" + t + "\"}"))
                .andExpect(status().isNotFound());
        String tooLong = body(t).replace("\"consentConfirmed\":true",
                "\"consentConfirmed\":true,\"overrides\":{\"notice\":\"" + "가".repeat(1100) + "\"}");
        assertThat(issue(owner, t, UUID.randomUUID().toString(), tooLong).getResponse().getStatus()).isEqualTo(422);
        UUID merchant = db.queryForObject("select merchant_id from coupon_templates where id=?", UUID.class,
                UUID.fromString(t));
        for (String table : List.of("coupons", "customers", "message_outbox", "monthly_message_usage",
                "idempotency_records"))
            assertThat(db.queryForObject("select count(*) from " + table + " where merchant_id=?", Integer.class,
                    merchant)).isZero();
    }

    @Test
    void disabledRowsNeverBecomeLiveByEnablingWorkerLater() throws Exception {
        Cookie owner = signup();
        JsonNode coupon = issued(owner, template(owner));
        db.update("update message_outbox set payload_version=1,dispatch_enabled=true where id=?", outbox(coupon));
        live();
        assertThat(worker.dispatchOnce()).isFalse();
        assertThat(db.queryForObject("select state from message_outbox where id=?", String.class, outbox(coupon)))
                .isEqualTo("QUEUED");
    }

    @Test
    void acceptedDeliveryWaitsForAuthenticatedFinalResultAndSettlesOnce() throws Exception {
        live();
        Cookie owner = signup();
        JsonNode coupon = issued(owner, template(owner));
        UUID attempt = outbox(coupon);
        when(provider.send(any())).thenAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).isFalse();
            return new SolapiClient.Submission(SolapiClient.SubmissionState.ACCEPTED, "message1", "group1", "2000",
                    null);
        });
        assertThat(worker.dispatchOnce()).isTrue();
        assertThat(worker.dispatchOnce()).isFalse();
        mvc.perform(get("/api/v1/coupons/" + coupon.get("id").asText()).cookie(owner))
                .andExpect(jsonPath("$.status").value("PENDING_SEND"))
                .andExpect(jsonPath("$.issueMessageStatus").value("SUBMITTED"));
        mvc.perform(post("/api/v1/webhooks/solapi").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());
        event("message1", attempt, "4000");
        results.processPending();
        event("message1", attempt, "4000");
        results.processPending();
        mvc.perform(get("/api/v1/coupons/" + coupon.get("id").asText()).cookie(owner))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.issueMessageStatus").value("DELIVERED"));
        assertThat(db.queryForObject(
                "select reserved_count from monthly_message_usage where merchant_id=(select merchant_id from coupons where id=?)",
                Integer.class, UUID.fromString(coupon.get("id").asText()))).isZero();
        assertThat(db.queryForObject(
                "select settled_count from monthly_message_usage where merchant_id=(select merchant_id from coupons where id=?)",
                Integer.class, UUID.fromString(coupon.get("id").asText()))).isEqualTo(1);
    }

    @Test
    void unknownSubmissionIsNotResentOrManuallyRetried() throws Exception {
        live();
        Cookie owner = signup();
        JsonNode coupon = issued(owner, template(owner));
        when(provider.send(any()))
                .thenReturn(new SolapiClient.Submission(SolapiClient.SubmissionState.UNKNOWN, null, null, null, null));
        assertThat(worker.dispatchOnce()).isTrue();
        assertThat(worker.dispatchOnce()).isFalse();
        mvc.perform(post("/api/v1/coupons/" + coupon.get("id").asText() + "/delivery-retry").cookie(owner)
                .header("Origin", origin).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/coupons/" + coupon.get("id").asText()).cookie(owner))
                .andExpect(jsonPath("$.issueMessageStatus").value("UNKNOWN"));
    }

    @Test
    void lostResponseIsRecoveredFromVerifiedHistoryWithoutWebhookOrResend() throws Exception {
        live();
        Cookie owner = signup();
        JsonNode coupon = issued(owner, template(owner));
        UUID attempt = outbox(coupon);
        when(provider.send(any()))
                .thenReturn(new SolapiClient.Submission(SolapiClient.SubmissionState.UNKNOWN, null, null, null, null));
        worker.dispatchOnce();
        when(provider.findAttempt(eq(attempt), any())).thenReturn(Optional.of(new SolapiClient.ProviderResult(
                "recovered-message", "group", "4000", "LMS", attempt, Instant.parse("2026-11-01T14:59:00Z"))));
        db.update("update message_deliveries set next_check_at=? where id=?",
                java.sql.Timestamp.from(Instant.parse("2026-11-01T14:59:00Z")), attempt);
        results.reconcile();
        mvc.perform(get("/api/v1/coupons/" + coupon.get("id").asText()).cookie(owner))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.issueMessageStatus").value("DELIVERED"));
        assertThat(worker.dispatchOnce()).isFalse();
        verify(provider, times(1)).send(any());
        assertThat(db.queryForObject("select settlement_status from message_outbox where id=?", String.class, attempt))
                .isEqualTo("SETTLED");
    }

    @Test
    void explicitRejectionCanBeRetriedIdempotentlyWithoutNewCoupon() throws Exception {
        live();
        Cookie owner = signup();
        JsonNode coupon = issued(owner, template(owner));
        when(provider.send(any())).thenReturn(
                new SolapiClient.Submission(SolapiClient.SubmissionState.REJECTED, null, null, "1030", null));
        worker.dispatchOnce();
        String key = UUID.randomUUID().toString();
        String endpoint = "/api/v1/coupons/" + coupon.get("id").asText() + "/delivery-retry";
        MvcResult first = mvc
                .perform(post(endpoint).cookie(owner).header("Origin", origin).header("Idempotency-Key", key))
                .andExpect(status().isAccepted()).andReturn();
        mvc.perform(post(endpoint).cookie(owner).header("Origin", origin).header("Idempotency-Key", key))
                .andExpect(status().isAccepted()).andExpect(content().json(first.getResponse().getContentAsString()));
        mvc.perform(post(endpoint).cookie(owner).header("Origin", origin).header("Idempotency-Key",
                UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from message_outbox where coupon_id=?", Integer.class,
                UUID.fromString(coupon.get("id").asText()))).isEqualTo(2);
    }

    @Test
    void verifiedEarlyResultSurvivesLateUnknownHttpResponse() throws Exception {
        live();
        Cookie owner = signup();
        JsonNode coupon = issued(owner, template(owner));
        UUID attempt = outbox(coupon);
        when(provider.send(any())).thenAnswer(invocation -> {
            when(provider.lookup("early-message")).thenReturn(Optional.of(new SolapiClient.ProviderResult(
                    "early-message", "group1", "3000", "LMS", attempt, Instant.parse("2026-11-01T15:00:00Z"))));
            event("early-message", attempt, "3000");
            results.processPending();
            return new SolapiClient.Submission(SolapiClient.SubmissionState.UNKNOWN, null, null, null, null);
        });
        worker.dispatchOnce();
        assertThat(db.queryForObject("select provider_message_id from message_deliveries where id=?", String.class,
                attempt)).isEqualTo("early-message");
        assertThat(db.queryForObject("select issue_message_status from coupons where id=?", String.class,
                UUID.fromString(coupon.get("id").asText()))).isEqualTo("SUBMITTED");
        when(provider.lookup("early-message")).thenReturn(Optional.empty());
        event("early-message", attempt, "4000");
        results.processPending();
        mvc.perform(get("/api/v1/coupons/" + coupon.get("id").asText()).cookie(owner))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deletedCustomerCannotBeActivatedByDelayedSuccess() throws Exception {
        live();
        Cookie owner = signup();
        JsonNode coupon = issued(owner, template(owner));
        UUID attempt = outbox(coupon);
        when(provider.send(any())).thenReturn(new SolapiClient.Submission(SolapiClient.SubmissionState.ACCEPTED,
                "deleted-message", "group1", "2000", null));
        worker.dispatchOnce();
        db.update(
                "update customers set phone_ciphertext=null,phone_lookup=null,deleted_at=current_timestamp where id=(select customer_id from coupons where id=?)",
                UUID.fromString(coupon.get("id").asText()));
        event("deleted-message", attempt, "4000");
        results.processPending();
        mvc.perform(get("/api/v1/coupons/" + coupon.get("id").asText()).cookie(owner))
                .andExpect(jsonPath("$.status").value("CLOSED_INACTIVE"));
    }

    @Test
    void latePreviousAttemptDoesNotActivateRetriedCouponAndMonthIsStable() throws Exception {
        live();
        Cookie owner = signup();
        JsonNode coupon = issued(owner, template(owner));
        UUID previous = outbox(coupon);
        when(provider.send(any())).thenReturn(new SolapiClient.Submission(SolapiClient.SubmissionState.ACCEPTED,
                "old-message", "group1", "2000", null));
        worker.dispatchOnce();
        event("old-message", previous, "3040");
        results.processPending();
        mvc.perform(post("/api/v1/coupons/" + coupon.get("id").asText() + "/delivery-retry").cookie(owner)
                .header("Origin", origin).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted());
        UUID current = outbox(coupon);
        event("old-message", previous, "4000");
        results.processPending();
        mvc.perform(get("/api/v1/coupons/" + coupon.get("id").asText()).cookie(owner))
                .andExpect(jsonPath("$.status").value("PENDING_SEND"));
        UUID merchant = db.queryForObject("select merchant_id from coupons where id=?", UUID.class,
                UUID.fromString(coupon.get("id").asText()));
        db.update("update monthly_message_usage set month_kst='2026-10-01' where merchant_id=?", merchant);
        db.update("update message_outbox set reservation_month='2026-10-01' where id=?", current);
        when(provider.send(any())).thenReturn(new SolapiClient.Submission(SolapiClient.SubmissionState.ACCEPTED,
                "new-message", "group1", "2000", null));
        worker.dispatchOnce();
        db.update("update coupons set starts_at='2026-10-01T00:00:00Z',expires_at='2026-10-31T00:00:00Z' where id=?",
                UUID.fromString(coupon.get("id").asText()));
        event("new-message", current, "4000");
        results.processPending();
        mvc.perform(get("/api/v1/coupons/" + coupon.get("id").asText()).cookie(owner))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        assertThat(db.queryForObject(
                "select settled_count from monthly_message_usage where merchant_id=? and month_kst='2026-10-01'",
                Integer.class, merchant)).isEqualTo(2);
        assertThat(db.queryForObject("select reserved_count from monthly_message_usage where merchant_id=?",
                Integer.class, merchant)).isZero();
    }

    @Test
    void leaseExpirationDoesNotLetAnotherWorkerSendAgain() throws Exception {
        live();
        Cookie owner = signup();
        JsonNode coupon = issued(owner, template(owner));
        UUID attempt = outbox(coupon);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(provider.send(any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Test synchronization timed out");
            return new SolapiClient.Submission(SolapiClient.SubmissionState.ACCEPTED, "lease-message", "group1", "2000",
                    null);
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> first = executor.submit(worker::dispatchOnce);
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            db.update("update message_outbox set lease_until='2020-01-01T00:00:00Z' where id=?", attempt);
            assertThat(worker.dispatchOnce()).isFalse();
            assertThat(db.queryForObject("select state from message_outbox where id=?", String.class, attempt))
                    .isEqualTo("UNKNOWN");
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(calls.get()).isEqualTo(1);
            assertThat(db.queryForObject("select state from message_outbox where id=?", String.class, attempt))
                    .isEqualTo("SUBMITTED");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

}
