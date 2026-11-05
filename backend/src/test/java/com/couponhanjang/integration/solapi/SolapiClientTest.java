package com.couponhanjang.integration.solapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SolapiClientTest {
    @Test
    void sendsAutomaticTypeAndTreatsAcceptanceAsSubmittedOnly() throws Exception {
        SolapiProperties config = new SolapiProperties();
        config.setApiKey("test-key");
        config.setApiSecret("test-secret");
        var builder = RestClient.builder().baseUrl("https://api.solapi.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new SolapiClient(builder.build(), config,
                Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC));
        server.expect(requestTo("https://api.solapi.com/messages/v4/send-many/detail"))
                .andExpect(header("Authorization",
                        org.hamcrest.Matchers.startsWith("HMAC-SHA256 apiKey=test-key, date=2026-11-05T00:00:00Z")))
                .andExpect(request -> {
                    var body = new ObjectMapper().readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString());
                    var message = body.get("messages").get(0);
                    assertThat(message.get("autoTypeDetect").asBoolean()).isTrue();
                    assertThat(message.has("type")).isFalse();
                    assertThat(message.has("subject")).isFalse();
                })
                .andRespond(withSuccess(
                        "{\"groupInfo\":{\"groupId\":\"group1\"},\"messageList\":[{\"messageId\":\"message1\",\"statusCode\":\"2000\"}]}",
                        MediaType.APPLICATION_JSON));
        var result = client.send(new SolapiClient.SendCommand(UUID.randomUUID(), "01012345678", "01011112222", "test"));
        assertThat(result.state()).isEqualTo(SolapiClient.SubmissionState.ACCEPTED);
        assertThat(result.messageId()).isEqualTo("message1");
        server.verify();
    }

    @Test
    void serverFailureIsUnknownRatherThanSafeToResend() {
        var config = new SolapiProperties();
        config.setApiKey("k");
        config.setApiSecret("s");
        var builder = RestClient.builder().baseUrl("https://api.solapi.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new SolapiClient(builder.build(), config, Clock.systemUTC());
        server.expect(requestTo("https://api.solapi.com/messages/v4/send-many/detail"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(client.send(new SolapiClient.SendCommand(UUID.randomUUID(), "01012345678", "01011112222", "test"))
                .state())
                .isEqualTo(SolapiClient.SubmissionState.UNKNOWN);
    }

    @Test
    void authenticationMatchesIndependentHmacFixture() {
        assertThat(SolapiAuthSigner.sign("test-key", "test-secret", "2026-11-05T00:00:00Z", "0123456789abcdef"))
                .isEqualTo(
                        "HMAC-SHA256 apiKey=test-key, date=2026-11-05T00:00:00Z, salt=0123456789abcdef, signature=94d990a76002e789bdc71e0e7cc93ef9b5a47dbf1e849430cb744dc4d1816a17");
    }

    @Test
    void lookupUsesProviderMessageIdentityAndRejectListIsNotAcceptance() {
        var config = new SolapiProperties();
        config.setApiKey("k");
        config.setApiSecret("s");
        var builder = RestClient.builder().baseUrl("https://api.solapi.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new SolapiClient(builder.build(), config, Clock.systemUTC());
        server.expect(requestTo("https://api.solapi.com/messages/v4/list?criteria=messageId&cond=eq&value=m1&limit=1"))
                .andRespond(withSuccess(
                        "{\"messageList\":{\"m1\":{\"messageId\":\"m1\",\"statusCode\":\"4000\",\"type\":\"LMS\"}}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.solapi.com/messages/v4/send-many/detail"))
                .andRespond(withSuccess("{\"failedMessageList\":[{\"messageId\":\"m2\",\"statusCode\":\"1030\"}]}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.lookup("m1").orElseThrow().code()).isEqualTo("4000");
        assertThat(client.send(new SolapiClient.SendCommand(UUID.randomUUID(), "01012345678", "01011112222", "test"))
                .state())
                .isEqualTo(SolapiClient.SubmissionState.REJECTED);
        server.verify();
    }

    @Test
    void historySearchBindsUniqueAttemptAcrossPagesAndRejectsAmbiguousMatches() {
        var config = new SolapiProperties();
        config.setApiKey("k");
        config.setApiSecret("s");
        var builder = RestClient.builder().baseUrl("https://api.solapi.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new SolapiClient(builder.build(), config, Clock.systemUTC());
        UUID attempt = UUID.randomUUID();
        String entry = "\"messageId\":\"m1\",\"statusCode\":\"4000\",\"customFields\":{\"attemptId\":\"" + attempt
                + "\"}";
        server.expect(queryParam("limit", "500")).andExpect(queryParam("dateType", "CREATED"))
                .andRespond(withSuccess("{\"messageList\":{},\"nextKey\":\"cursor\"}", MediaType.APPLICATION_JSON));
        server.expect(queryParam("startKey", "cursor"))
                .andRespond(withSuccess("{\"messageList\":{\"m1\":{" + entry + "}}}", MediaType.APPLICATION_JSON));
        server.expect(queryParam("limit", "500"))
                .andRespond(withSuccess(
                        "{\"messageList\":{\"m1\":{" + entry + "},\"m2\":{" + entry.replace("m1", "m2") + "}}}",
                        MediaType.APPLICATION_JSON));
        Instant requested = Instant.parse("2026-11-05T00:00:00Z");
        assertThat(client.findAttempt(attempt, requested).orElseThrow().messageId()).isEqualTo("m1");
        assertThat(client.findAttempt(attempt, requested)).isEmpty();
        server.verify();
    }

    @Test
    void definiteAuthenticationFailureIsRejectedButNetworkTimeoutRemainsUnknown() {
        var config = new SolapiProperties();
        config.setApiKey("k");
        config.setApiSecret("s");
        var builder = RestClient.builder().baseUrl("https://api.solapi.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new SolapiClient(builder.build(), config, Clock.systemUTC());
        server.expect(requestTo("https://api.solapi.com/messages/v4/send-many/detail"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://api.solapi.com/messages/v4/send-many/detail"))
                .andRespond(withException(new java.net.SocketTimeoutException("test timeout")));
        var command = new SolapiClient.SendCommand(UUID.randomUUID(), "01012345678", "01011112222", "test");
        assertThat(client.send(command).state()).isEqualTo(SolapiClient.SubmissionState.REJECTED);
        assertThat(client.send(command).state()).isEqualTo(SolapiClient.SubmissionState.UNKNOWN);
        server.verify();
    }

}
