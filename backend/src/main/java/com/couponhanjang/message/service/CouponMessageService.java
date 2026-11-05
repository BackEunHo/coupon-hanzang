package com.couponhanjang.message.service;

import com.couponhanjang.common.api.JsonInput;
import com.couponhanjang.coupon.service.CouponExpiryCalculator;
import com.couponhanjang.merchant.repository.MerchantRepository;
import com.couponhanjang.message.dto.MessagePreviewResponse;
import com.couponhanjang.template.dto.TemplateValues;
import com.couponhanjang.template.repository.CouponTemplateRepository;
import com.couponhanjang.template.service.CouponTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class CouponMessageService {
    private final MerchantRepository merchants;
    private final CouponTemplateRepository templates;
    private final CouponExpiryCalculator expiry;
    private final CouponMessageRenderer renderer;
    private final MessageLengthValidator validator;
    private final Clock clock;

    public CouponMessageService(MerchantRepository merchants, CouponTemplateRepository templates,
            CouponExpiryCalculator expiry, CouponMessageRenderer renderer, MessageLengthValidator validator,
            Clock clock) {
        this.merchants = merchants;
        this.templates = templates;
        this.expiry = expiry;
        this.renderer = renderer;
        this.validator = validator;
        this.clock = clock;
    }

    public String compose(UUID merchantId, TemplateValues values,
            CouponExpiryCalculator.ResolvedExpiry validity, String code) {
        String name = merchants.findById(merchantId).orElseThrow(CouponTemplateService::notFound).getName();
        return renderer.render(values, name, validity, code);
    }

    @Transactional(readOnly = true)
    public MessagePreviewResponse preview(UUID merchantId, JsonNode body) {
        JsonInput.fields(body, Set.of("templateId", "overrides"), true);
        UUID id = JsonInput.uuid(JsonInput.text(body, "templateId", 36, false));
        var template = templates.findByIdAndMerchantIdAndDeletedAtIsNull(id, merchantId)
                .orElseThrow(CouponTemplateService::notFound);
        TemplateValues values = body.has("overrides")
                ? TemplateValues.parse(body.get("overrides"), template.values())
                : template.values();
        Instant now = clock.instant();
        String text = compose(merchantId, values, expiry.resolve(values.expiry(), now), "******");
        var inspection = validator.inspect(text);
        return new MessagePreviewResponse(text, inspection.byteLength(), inspection.expectedMessageType(),
                inspection.sendable(), inspection.validationErrors(), now);
    }
}
