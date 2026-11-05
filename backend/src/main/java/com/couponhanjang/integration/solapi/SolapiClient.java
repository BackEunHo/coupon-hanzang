package com.couponhanjang.integration.solapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.http.MediaType;
import java.time.*;
import java.util.*;

@Component
public class SolapiClient {
    private final RestClient http;
    private final SolapiProperties properties;
    private final Clock clock;

    public SolapiClient(@Qualifier("solapiRestClient") RestClient http, SolapiProperties properties, Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    public enum SubmissionState {
        ACCEPTED, REJECTED, UNKNOWN
    }

    public record SendCommand(UUID attemptId, String to, String from, String text) {
        @Override
        public String toString() {
            return "SendCommand[REDACTED]";
        }
    }

    public record Submission(SubmissionState state, String messageId, String groupId, String code, String type) {
    }

    public record ProviderResult(String messageId, String groupId, String code, String type, UUID attemptId,
            Instant reportedAt) {
    }

    public Submission send(SendCommand command) {
        var message = Map.of("to", command.to(), "from", command.from(), "text", command.text(),
                "autoTypeDetect", true, "customFields", Map.of("attemptId", command.attemptId().toString()));
        try {
            JsonNode result = http.post().uri("/messages/v4/send-many/detail").header("Authorization", auth())
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("messages", List.of(message),
                            "showMessageList", true, "allowDuplicates", false))
                    .retrieve().body(JsonNode.class);
            if (result == null)
                return unknown();
            String group = text(result.path("groupInfo"), "groupId");
            JsonNode failures = result.path("failedMessageList");
            if (failures.isArray() && !failures.isEmpty()) {
                return new Submission(SubmissionState.REJECTED, text(failures.get(0), "messageId"), group,
                        text(failures.get(0), "statusCode"), text(failures.get(0), "type"));
            }
            JsonNode list = result.path("messageList");
            if (!list.isArray() || list.size() != 1)
                return new Submission(SubmissionState.UNKNOWN, null, group, null, null);
            JsonNode entry = list.get(0);
            String id = text(entry, "messageId"), code = text(entry, "statusCode");
            if (id == null)
                return unknown();
            return new Submission(
                    code != null && code.startsWith("1") ? SubmissionState.REJECTED : SubmissionState.ACCEPTED,
                    id, group, code, text(entry, "type"));
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            return status >= 400 && status < 500 && status != 408
                    ? new Submission(SubmissionState.REJECTED, null, null, "HTTP_" + status, null)
                    : unknown();
        } catch (RestClientException error) {
            return unknown();
        }
    }

    public Optional<ProviderResult> lookup(String messageId) {
        try {
            JsonNode result = http.get().uri(builder -> builder.path("/messages/v4/list")
                    .queryParam("criteria", "messageId").queryParam("cond", "eq").queryParam("value", messageId)
                    .queryParam("limit", 1).build()).header("Authorization", auth()).retrieve().body(JsonNode.class);
            if (result == null)
                return Optional.empty();
            JsonNode entry = result.path("messageList").path(messageId);
            if (!messageId.equals(text(entry, "messageId")))
                return Optional.empty();
            return Optional.of(parseResult(entry));
        } catch (RestClientException error) {
            return Optional.empty();
        }
    }

    /**
     * Search a bounded creation window; bind only one match after all pages were
     * read.
     */
    public Optional<ProviderResult> findAttempt(UUID attemptId, Instant requestedAt) {
        ProviderResult match = null;
        String nextKey = null;
        try {
            for (int page = 0; page < 3; page++) {
                final String cursor = nextKey;
                JsonNode result = http.get().uri(builder -> {
                    builder.path("/messages/v4/list")
                            .queryParam("startDate", requestedAt.minusSeconds(300).toString())
                            .queryParam("endDate", requestedAt.plusSeconds(300).toString())
                            .queryParam("dateType", "CREATED").queryParam("limit", 500);
                    if (cursor != null)
                        builder.queryParam("startKey", cursor);
                    return builder.build();
                }).header("Authorization", auth()).retrieve().body(JsonNode.class);
                if (result == null || !result.path("messageList").isObject())
                    return Optional.empty();
                for (JsonNode entry : result.path("messageList")) {
                    ProviderResult candidate = parseResult(entry);
                    if (!attemptId.equals(candidate.attemptId()) || candidate.messageId() == null)
                        continue;
                    if (match != null && !match.messageId().equals(candidate.messageId()))
                        return Optional.empty();
                    match = candidate;
                }
                nextKey = text(result, "nextKey");
                if (nextKey == null)
                    return Optional.ofNullable(match);
            }
        } catch (RestClientException error) {
            return Optional.empty();
        }
        // A truncated history cannot prove uniqueness. Keep UNKNOWN and never resend.
        return Optional.empty();
    }

    private ProviderResult parseResult(JsonNode entry) {
        UUID attempt = null;
        try {
            attempt = UUID.fromString(entry.path("customFields").path("attemptId").asText());
        } catch (IllegalArgumentException ignored) {
        }
        Instant reported = clock.instant();
        try {
            reported = Instant.parse(entry.path("dateReported").asText());
        } catch (DateTimeException ignored) {
        }
        return new ProviderResult(text(entry, "messageId"), text(entry, "groupId"), text(entry, "statusCode"),
                text(entry, "type"), attempt, reported);
    }

    private String auth() {
        return SolapiAuthSigner.sign(properties.getApiKey(), properties.getApiSecret(), clock.instant().toString(),
                UUID.randomUUID().toString().replace("-", ""));
    }

    private static String text(JsonNode value, String field) {
        JsonNode node = value.path(field);
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private Submission unknown() {
        return new Submission(SubmissionState.UNKNOWN, null, null, null, null);
    }
}
