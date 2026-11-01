package com.couponhanjang.template.dto;

import com.couponhanjang.template.entity.CouponTemplate;
import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(UUID id, String title, String benefit, String notice, ExpiryRule expiry,
        Instant createdAt, Instant updatedAt) {
    public static TemplateResponse from(CouponTemplate t) {
        TemplateValues v = t.values();
        return new TemplateResponse(t.getId(), v.title(), v.benefit(), v.notice(), v.expiry(), t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
