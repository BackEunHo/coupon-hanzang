package com.couponhanjang.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CouponTemplateApiIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    final String origin = "http://localhost:8080";
    String body = """
            {"title":"할인 쿠폰","benefit":"1000원 할인","notice":"매장에서 사용",
             "expiry":{"mode":"RELATIVE_DAYS","durationDays":14}}
            """;

    Cookie signup() throws Exception {
        String phone = "010" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").concat("12345678").substring(0, 8);
        return mvc.perform(post("/api/v1/auth/signup").header("Origin", origin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"merchantName\":\"상점\",\"phone\":\"" + phone + "\",\"password\":\"password-123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    @Test
    void crudIsTenantScopedAndDeletionIsPermanentForTheApi() throws Exception {
        Cookie owner = signup(), other = signup();
        MvcResult created = mvc.perform(post("/api/v1/templates").cookie(owner).header("Origin", origin)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/v1/templates/" + id).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/templates/" + id).cookie(owner).header("Origin", origin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"notice\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("할인 쿠폰"))
                .andExpect(jsonPath("$.notice").value(""));
        mvc.perform(delete("/api/v1/templates/" + id).cookie(other).header("Origin", origin))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/templates/" + id).cookie(owner).header("Origin", origin))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/templates/" + id).cookie(owner).header("Origin", origin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/templates/" + id).cookie(owner)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/templates").cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.page.page").value(0));
    }

    @Test
    void malformedChangesAndPagingAreRejected() throws Exception {
        Cookie session = signup();
        for (String invalid : new String[] { "{}", body.replace("14", "0"),
                body.replace("\"title\":\"할인 쿠폰\"", "\"title\":\" \""),
                body.replace("\"notice\":", "\"extra\":1,\"notice\":") }) {
            mvc.perform(post("/api/v1/templates").cookie(session).header("Origin", origin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/templates?size=101").cookie(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/templates?page=2147483647&size=100").cookie(session))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/templates/not-a-uuid").cookie(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/templates")).andExpect(status().isUnauthorized());
        mvc.perform(options("/api/v1/templates/" + UUID.randomUUID()).header("Origin", origin)
                .header("Access-Control-Request-Method", "DELETE")).andExpect(status().isOk());
    }
}
