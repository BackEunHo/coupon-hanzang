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

@SpringBootTest(properties = "app.quota.monthly-message-limit=2")
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "postgres-test" })
@Testcontainers
@Import(CouponApiPostgresIntegrationTest.FixedTime.class)
class CouponApiPostgresIntegrationTest {
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

    @Test
    void issueSnapshotsSurviveTemplateChangesAndTenantQueriesAreIsolated() throws Exception {
        Cookie owner = signup(), other = signup();
        String t = template(owner), key = UUID.randomUUID().toString();
        MvcResult first = issue(owner, t, key, body(t));
        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        JsonNode coupon = json.readTree(first.getResponse().getContentAsString());
        String id = coupon.get("id").asText();
        assertThat(coupon.get("expiresAt").asText()).isEqualTo("2026-11-14T15:00:00Z");
        assertThat(coupon.get("status").asText()).isEqualTo("PENDING_SEND");
        assertThat(coupon.has("code")).isFalse();
        assertThat(coupon.has("codeMac")).isFalse();
        mvc.perform(patch("/api/v1/templates/" + t).cookie(owner).header("Origin", origin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정 쿠폰\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/templates/" + t).cookie(owner).header("Origin", origin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/coupons/" + id).cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("첫 쿠폰"));
        assertThat(issue(owner, t, key, body(t)).getResponse().getStatus()).isEqualTo(202);
        assertThat(issue(owner, t, UUID.randomUUID().toString(), body(t)).getResponse().getStatus()).isEqualTo(404);
        mvc.perform(get("/api/v1/coupons/" + id).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/coupons?phone=01012345678&title=첫&status=PENDING_SEND").cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/coupons?phone=01012345678").cookie(other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        assertThat(db.queryForObject("select count(*) from message_outbox where coupon_id=?", Integer.class,
                UUID.fromString(id))).isEqualTo(1);
        assertThat(db.queryForObject(
                "select phone_ciphertext from customers where id=(select customer_id from coupons where id=?)",
                String.class, UUID.fromString(id))).doesNotContain("01012345678");
    }

    @Test
    void duplicateRequestsAndQuotaAreAtomic() throws Exception {
        Cookie owner = signup();
        String t = template(owner), key = UUID.randomUUID().toString();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<MvcResult>> repeated = new ArrayList<>();
            for (int i = 0; i < 4; i++)
                repeated.add(workers.submit(() -> issue(owner, t, key, body(t))));
            Set<String> ids = new HashSet<>();
            for (Future<MvcResult> f : repeated) {
                MvcResult r = f.get(20, TimeUnit.SECONDS);
                assertThat(r.getResponse().getStatus()).isEqualTo(202);
                ids.add(json.readTree(r.getResponse().getContentAsString()).get("id").asText());
            }
            assertThat(ids).hasSize(1);
            assertThat(issue(owner, t, key, body(t).replace("1234-5678", "9999-9999")).getResponse().getStatus())
                    .isEqualTo(409);
            List<Future<Integer>> distinct = new ArrayList<>();
            for (int i = 0; i < 4; i++)
                distinct.add(workers.submit(
                        () -> issue(owner, t, UUID.randomUUID().toString(), body(t)).getResponse().getStatus()));
            List<Integer> results = new ArrayList<>();
            for (var f : distinct)
                results.add(f.get(20, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(202, 429, 429, 429);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void invalidIssueDoesNotCreateQuotaOrCoupon() throws Exception {
        Cookie owner = signup(), other = signup();
        String t = template(owner);
        assertThat(issue(other, t, UUID.randomUUID().toString(), body(t)).getResponse().getStatus()).isEqualTo(404);
        assertThat(issue(owner, t, UUID.randomUUID().toString(), body(t).replace("true", "false")).getResponse()
                .getStatus()).isEqualTo(400);
        assertThat(issue(owner, t, "bad-key", body(t)).getResponse().getStatus()).isEqualTo(400);
        assertThat(issue(owner, t, UUID.randomUUID().toString(), body(t).replace(t, "1-1-1-1-1")).getResponse()
                .getStatus()).isEqualTo(400);
        mvc.perform(get("/api/v1/coupons").cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void overridesOnlyAffectIssuedSnapshotAndDeletedPhonesAreMasked() throws Exception {
        Cookie owner = signup();
        String t = template(owner);
        String request = body(t).replace("\"consentConfirmed\":true",
                "\"consentConfirmed\":true,\"overrides\":{\"title\":\"개별 쿠폰\",\"notice\":\"\",\"expiry\":{\"mode\":\"RELATIVE_DAYS\",\"durationDays\":1}}");
        MvcResult result = issue(owner, t, UUID.randomUUID().toString(), request);
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        String id = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/v1/coupons/" + id).cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("개별 쿠폰"))
                .andExpect(jsonPath("$.notice").value(""))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-01T15:00:00Z"));
        mvc.perform(get("/api/v1/templates/" + t).cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("첫 쿠폰"))
                .andExpect(jsonPath("$.expiry.durationDays").value(14));
        mvc.perform(get("/api/v1/coupons").param("title", "%").cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        UUID couponId = UUID.fromString(id);
        db.update(
                "update customers set phone_ciphertext=null,phone_lookup=null,deleted_at=current_timestamp where id=(select customer_id from coupons where id=?)",
                couponId);
        mvc.perform(get("/api/v1/coupons/" + id).cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayPhone").value("010-****-****"))
                .andExpect(jsonPath("$.customerDataDeleted").value(true));
        mvc.perform(get("/api/v1/coupons?phone=01012345678").cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        db.update("update coupons set starts_at='2026-10-30T00:00:00Z',expires_at='2026-11-01T00:00:00Z' where id=?",
                couponId);
        mvc.perform(get("/api/v1/coupons?status=EXPIRED").cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("EXPIRED"));
        mvc.perform(get("/api/v1/coupons?status=PENDING_SEND").cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void outboxFailureRollsBackCouponCustomerQuotaAndIdempotency() throws Exception {
        Cookie owner = signup();
        String t = template(owner), key = UUID.randomUUID().toString();
        UUID merchant = db.queryForObject("select merchant_id from coupon_templates where id=?", UUID.class,
                UUID.fromString(t));
        db.execute("alter table message_outbox add constraint test_reject_outbox check (merchant_id <> '" + merchant
                + "'::uuid) not valid");
        try {
            assertThat(issue(owner, t, key, body(t)).getResponse().getStatus()).isEqualTo(409);
            for (String table : List.of("coupons", "customers", "monthly_message_usage", "idempotency_records",
                    "message_outbox"))
                assertThat(db.queryForObject("select count(*) from " + table + " where merchant_id=?", Integer.class,
                        merchant)).isZero();
        } finally {
            db.execute("alter table message_outbox drop constraint test_reject_outbox");
        }
        assertThat(issue(owner, t, key, body(t)).getResponse().getStatus()).isEqualTo(202);
        assertThat(db.queryForObject("select reserved_count from monthly_message_usage where merchant_id=?",
                Integer.class, merchant)).isEqualTo(1);
    }

}
