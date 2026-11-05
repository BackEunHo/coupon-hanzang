package com.couponhanjang.coupon.service;

import com.couponhanjang.common.api.JsonInput;
import com.couponhanjang.common.exception.ApiException;
import com.couponhanjang.coupon.dto.CouponResponse;
import com.couponhanjang.coupon.entity.Coupon;
import com.couponhanjang.coupon.repository.CouponIssuanceStore;
import com.couponhanjang.coupon.repository.CouponRepository;
import com.couponhanjang.template.dto.TemplateValues;
import com.couponhanjang.template.repository.CouponTemplateRepository;
import com.couponhanjang.template.service.CouponTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CouponIssueService {
    private final CouponRepository coupons;
    private final CouponTemplateRepository templates;
    private final CouponIssuanceStore store;
    private final CouponQueryService query;
    private final CustomerPhoneProtector phones;
    private final CouponCodeProtector codes;
    private final CouponExpiryCalculator expiry;
    private final ObjectMapper json;
    private final Clock clock;
    private final com.couponhanjang.message.service.CouponMessageService messages;
    private final com.couponhanjang.message.service.MessageLengthValidator length;
    private final com.couponhanjang.integration.solapi.SolapiProperties sending;
    private final int monthlyLimit;

    public CouponIssueService(CouponRepository coupons, CouponTemplateRepository templates,
            CouponIssuanceStore store, CouponQueryService query,
            CustomerPhoneProtector phones, CouponCodeProtector codes,
            CouponExpiryCalculator expiry, ObjectMapper json, Clock clock,
            com.couponhanjang.message.service.CouponMessageService messages,
            com.couponhanjang.message.service.MessageLengthValidator length,
            com.couponhanjang.integration.solapi.SolapiProperties sending,
            @Value("${app.quota.monthly-message-limit}") int monthlyLimit) {
        this.messages = messages;
        this.length = length;
        this.sending = sending;
        this.coupons = coupons;
        this.templates = templates;
        this.store = store;
        this.query = query;
        this.phones = phones;
        this.codes = codes;
        this.expiry = expiry;
        this.json = json;
        this.clock = clock;
        this.monthlyLimit = monthlyLimit;
        if (monthlyLimit < 1) {
            throw new IllegalArgumentException("Monthly message limit must be positive");
        }
    }

    @Transactional
    public CouponResponse issue(UUID merchantId, UUID operatorId, UUID key, JsonNode body) {
        JsonInput.fields(body, Set.of("templateId", "recipientPhone", "consentConfirmed", "overrides"), true);
        UUID templateId = JsonInput.uuid(JsonInput.text(body, "templateId", 36, false));
        String phone = phones.normalize(JsonInput.text(body, "recipientPhone", 20, false));
        if (!body.has("consentConfirmed") || !body.get("consentConfirmed").isBoolean()
                || !body.get("consentConfirmed").booleanValue()) {
            throw JsonInput.invalid();
        }
        if (body.has("overrides")) {
            TemplateValues.validatePatch(body.get("overrides"));
        }

        ObjectNode normalized = body.deepCopy();
        normalized.put("recipientPhone", phone);
        String fingerprint = phones.fingerprint(canonical(normalized).toString());
        Instant now = clock.instant();

        // 멱등성 키를 먼저 확보한다. 경합 요청은 최초 트랜잭션의 커밋을 기다린다.
        if (!store.claim(merchantId, key, fingerprint, now)) {
            var previous = store.previous(merchantId, key);
            if (!previous.fingerprint().equals(fingerprint)) {
                throw new ApiException(409, "IDEMPOTENCY_CONFLICT",
                        "Idempotency key was used for a different request");
            }
            return query.get(merchantId, previous.couponId());
        }

        var template = templates.findForIssue(templateId, merchantId)
                .orElseThrow(CouponTemplateService::notFound);
        TemplateValues values = body.has("overrides")
                ? TemplateValues.parse(body.get("overrides"), template.values())
                : template.values();
        var validity = expiry.resolve(values.expiry(), now);
        UUID couponId = UUID.randomUUID();
        var code = codes.generate(couponId);
        String text = messages.compose(merchantId, values, validity, code.plaintext());
        var inspection = length.requireSendable(text);
        if (sending.isSendEnabled() && !sending.allows(phone))
            throw new ApiException(403, "RECIPIENT_NOT_ALLOWED", "Recipient is not allowed for test sending");
        LocalDate month = now.atZone(CouponExpiryCalculator.ZONE).toLocalDate().withDayOfMonth(1);
        if (!store.reserve(merchantId, month, monthlyLimit)) {
            throw new ApiException(429, "QUOTA_EXCEEDED", "Monthly message quota exceeded");
        }

        UUID customerId = store.customer(merchantId, phones.lookup(merchantId, phone), phones.encrypt(phone), now);
        Coupon coupon = new Coupon(couponId, merchantId, templateId, customerId,
                values, now, validity.startsAt(), validity.expiresAt(), operatorId, code.mac());
        coupon.recordMessagePlan(inspection.expectedMessageType(), inspection.byteLength());
        coupons.saveAndFlush(coupon);

        ObjectNode payload = json.createObjectNode();
        payload.put("text", text);
        payload.put("from", sending.getFromNumber());
        payload.put("couponId", couponId.toString());
        store.enqueue(merchantId, couponId, phones.encrypt(phone), codes.encryptPayload(payload.toString()), now,
                inspection.expectedMessageType(), inspection.byteLength(), sending.isSendEnabled());
        store.complete(merchantId, key, couponId);
        return query.response(coupon);
    }

    private JsonNode canonical(JsonNode value) {
        if (!value.isObject()) {
            return value;
        }
        ObjectNode sorted = json.createObjectNode();
        List<String> names = new ArrayList<>();
        value.fieldNames().forEachRemaining(names::add);
        Collections.sort(names);
        names.forEach(name -> sorted.set(name, canonical(value.get(name))));
        return sorted;
    }
}
