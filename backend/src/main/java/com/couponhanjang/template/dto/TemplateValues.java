package com.couponhanjang.template.dto;

import com.couponhanjang.common.api.JsonInput;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

public record TemplateValues(String title, String benefit, String notice, ExpiryRule expiry) {
    public static void validatePatch(JsonNode body) {
        JsonInput.fields(body, Set.of("title", "benefit", "notice", "expiry"), true);
        if (body.has("title"))
            JsonInput.text(body, "title", 100, false);
        if (body.has("benefit"))
            JsonInput.text(body, "benefit", 500, false);
        if (body.has("notice"))
            JsonInput.text(body, "notice", 2000, true);
        if (body.has("expiry"))
            ExpiryRule.parse(body.get("expiry"));
    }

    public static TemplateValues parse(JsonNode body, TemplateValues base) {
        validatePatch(body);
        return new TemplateValues(
                body.has("title") || base == null ? JsonInput.text(body, "title", 100, false) : base.title(),
                body.has("benefit") || base == null ? JsonInput.text(body, "benefit", 500, false) : base.benefit(),
                body.has("notice") || base == null ? JsonInput.text(body, "notice", 2000, true) : base.notice(),
                body.has("expiry") || base == null ? ExpiryRule.parse(body.get("expiry")) : base.expiry());
    }
}
