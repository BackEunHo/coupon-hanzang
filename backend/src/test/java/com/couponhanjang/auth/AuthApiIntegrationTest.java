package com.couponhanjang.auth;

import com.couponhanjang.merchant.entity.Operator;

import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthApiIntegrationTest.ClockConfig.class)
class AuthApiIntegrationTest {
    private static final String ORIGIN = "http://localhost:8080";
    private static final String PHONE = "01012345678";
    private static final String PASSWORD = "old-password-123";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void reset() {
        jdbc.update("delete from operators");
        jdbc.update("delete from merchants");
        clock.set(Instant.parse("2026-10-25T00:00:00Z"));
    }

    @Test
    void signupCreatesUnverifiedMerchantAndRejectsDuplicatePhone() throws Exception {
        MvcResult first = signup(PHONE, PASSWORD);
        assertThat(first.getResponse().getCookie("SESSION")).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from operators where phone_verified_at is null", Integer.class)).isEqualTo(1);

        mvc.perform(post("/api/v1/auth/signup")
                .header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(PHONE, PASSWORD)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
    }

    @Test
    void loginRejectsWrongPasswordAndLogoutRevokesSession() throws Exception {
        signup(PHONE, PASSWORD);
        mvc.perform(post("/api/v1/auth/login")
                .header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(PHONE, "wrong-password")))
            .andExpect(status().isUnauthorized());

        Cookie session = sessionOf(mvc.perform(post("/api/v1/auth/login")
                .header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(PHONE, PASSWORD)))
            .andExpect(status().isOk()).andReturn().getResponse());

        mvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN).cookie(session))
            .andExpect(status().isNoContent());
        mvc.perform(patch("/api/v1/me/password").header("Origin", ORIGIN).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, "new-password-123")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRevokesOtherSessionsAndOldPassword() throws Exception {
        Cookie firstSession = sessionOf(signup(PHONE, PASSWORD).getResponse());
        Cookie secondSession = sessionOf(mvc.perform(post("/api/v1/auth/login")
                .header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(PHONE, PASSWORD)))
            .andExpect(status().isOk()).andReturn().getResponse());

        mvc.perform(patch("/api/v1/me/password").header("Origin", ORIGIN).cookie(secondSession)
                .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, "new-password-123")))
            .andExpect(status().isNoContent());
        mvc.perform(patch("/api/v1/me/password").header("Origin", ORIGIN).cookie(firstSession)
                .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, "another-password-123")))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(PHONE, PASSWORD)))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(PHONE, "new-password-123")))
            .andExpect(status().isOk());
    }

    @Test
    void withdrawalRevokesSessionsAndReleasesPhone() throws Exception {
        Cookie session = sessionOf(signup(PHONE, PASSWORD).getResponse());
        mvc.perform(post("/api/v1/auth/withdrawal").header("Origin", ORIGIN).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(PHONE, PASSWORD)))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/signup").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(signupBody(PHONE, PASSWORD)))
            .andExpect(status().isCreated());
    }

    @Test
    void absoluteDeadlineAndOriginGuardRejectRequests() throws Exception {
        Cookie session = sessionOf(signup(PHONE, PASSWORD).getResponse());
        mvc.perform(patch("/api/v1/me/password").cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, "new-password-123")))
            .andExpect(status().isForbidden());
        clock.set(Instant.parse("2026-10-26T00:00:00Z"));
        mvc.perform(patch("/api/v1/me/password").header("Origin", ORIGIN).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, "new-password-123")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedWrongPasswordsAreTemporarilyRateLimited() throws Exception {
        signup(PHONE, PASSWORD);
        for (int attempt = 1; attempt <= 4; attempt++) {
            mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON).content(loginBody(PHONE, "wrong-password")))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(PHONE, "wrong-password")))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
        mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(PHONE, PASSWORD)))
            .andExpect(status().isTooManyRequests());

        clock.set(Instant.parse("2026-10-25T00:15:00Z"));
        mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(PHONE, PASSWORD)))
            .andExpect(status().isOk());
    }

    @Test
    void stalePasswordUpdateCannotRestoreWithdrawnPhoneData() throws Exception {
        signup(PHONE, PASSWORD);
        java.util.UUID operatorId = jdbc.queryForObject("select id from operators", java.util.UUID.class);
        EntityManager withdrawal = entityManagerFactory.createEntityManager();
        EntityManager stalePasswordChange = entityManagerFactory.createEntityManager();
        try {
            withdrawal.getTransaction().begin();
            stalePasswordChange.getTransaction().begin();
            Operator toWithdraw = withdrawal.find(Operator.class, operatorId);
            Operator stale = stalePasswordChange.find(Operator.class, operatorId);
            toWithdraw.withdraw(clock.instant());
            withdrawal.getTransaction().commit();

            stale.changePassword("stale-password-hash");
            assertThatThrownBy(() -> stalePasswordChange.getTransaction().commit())
                .isInstanceOf(jakarta.persistence.RollbackException.class);
            assertThat(jdbc.queryForObject("select count(*) from operators where id = ? and phone_lookup is null and phone_ciphertext is null and password_hash is null", Integer.class, operatorId))
                .isEqualTo(1);
        } finally {
            if (withdrawal.getTransaction().isActive()) withdrawal.getTransaction().rollback();
            if (stalePasswordChange.getTransaction().isActive()) stalePasswordChange.getTransaction().rollback();
            withdrawal.close();
            stalePasswordChange.close();
        }
    }

    @Test
    void validLoginCanReplaceAStaleSessionCookie() throws Exception {
        Cookie stale = sessionOf(signup(PHONE, PASSWORD).getResponse());
        Cookie current = sessionOf(mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(PHONE, PASSWORD)))
            .andExpect(status().isOk()).andReturn().getResponse());
        mvc.perform(patch("/api/v1/me/password").header("Origin", ORIGIN).cookie(current)
                .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, "new-password-123")))
            .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN).cookie(stale)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(PHONE, "new-password-123")))
            .andExpect(status().isOk());
    }

    @Test
    void configuredFrontendOriginCanPreflightCookieLogin() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGIN))
            .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isEqualTo("true"));
    }

    @Test
    void passwordConfirmationAttemptsAreThrottledAcrossSensitiveEndpoints() throws Exception {
        Cookie session = sessionOf(signup(PHONE, PASSWORD).getResponse());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(patch("/api/v1/me/password").header("Origin", ORIGIN).cookie(session)
                    .contentType(MediaType.APPLICATION_JSON).content(passwordBody("wrong-password", "new-password-123")))
                .andExpect(status().isBadRequest());
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/auth/withdrawal").header("Origin", ORIGIN).cookie(session)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/auth/withdrawal").header("Origin", ORIGIN).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-password\"}"))
            .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/v1/auth/withdrawal").header("Origin", ORIGIN).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isTooManyRequests());

        clock.set(Instant.parse("2026-10-25T00:15:00Z"));
        mvc.perform(post("/api/v1/auth/withdrawal").header("Origin", ORIGIN).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isNoContent());
    }

    private MvcResult signup(String phone, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(signupBody(phone, password)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.merchantName").value("우리동네 커피"))
            .andExpect(jsonPath("$.phoneVerified").value(false))
            .andReturn();
    }

    private static Cookie sessionOf(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static String signupBody(String phone, String password) {
        return "{\"merchantName\":\"우리동네 커피\",\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}";
    }

    private static String loginBody(String phone, String password) {
        return "{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}";
    }

    private static String passwordBody(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(Instant.EPOCH);

        void set(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current.get(); }
    }
}
