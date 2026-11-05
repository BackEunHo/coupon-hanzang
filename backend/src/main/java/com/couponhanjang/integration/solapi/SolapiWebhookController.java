package com.couponhanjang.integration.solapi;

import com.couponhanjang.common.api.JsonInput;
import com.couponhanjang.message.repository.MessageDeliveryStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
public class SolapiWebhookController {
    private final MessageDeliveryStore store;
    private final Clock clock;

    public SolapiWebhookController(MessageDeliveryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @PostMapping("/api/v1/webhooks/solapi")
    public void receive(@RequestBody JsonNode body) {
        if (!body.isArray() || body.size() > 500)
            throw JsonInput.invalid();
        List<MessageDeliveryStore.Event> events = new ArrayList<>();
        for (JsonNode entry : body) {
            if (!entry.isObject())
                throw JsonInput.invalid();
            String type = JsonInput.text(entry, "type", 20, false);
            if (MessageDeliveryStore.safeType(type) == null)
                continue;
            String id = JsonInput.text(entry, "messageId", 100, false),
                    code = JsonInput.text(entry, "statusCode", 10, false);
            if (!code.matches("[0-9]{4}"))
                throw JsonInput.invalid();
            UUID attempt = null;
            if (entry.path("customFields").has("attemptId"))
                attempt = JsonInput.uuid(JsonInput.text(entry.get("customFields"), "attemptId", 36, false));
            Instant reported = clock.instant();
            if (entry.hasNonNull("dateReported")) {
                try {
                    reported = Instant.parse(JsonInput.text(entry, "dateReported", 40, false));
                } catch (DateTimeException error) {
                    throw JsonInput.invalid();
                }
            }
            events.add(new MessageDeliveryStore.Event(UUID.randomUUID(), id, attempt, code, type, reported));
        }
        // 최소 메타데이터만 저장·커밋한 뒤 응답한다. 전화번호·본문·상태 메시지는 보관하지 않는다.
        store.receive(events);
    }
}
