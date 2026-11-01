package com.couponhanjang.template.dto;

import com.couponhanjang.template.entity.ExpiryMode;
import com.couponhanjang.common.api.JsonInput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpiryRule(ExpiryMode mode, LocalDate startDate, LocalDate date, Integer durationDays) {
    public static ExpiryRule parse(JsonNode node) {
        JsonInput.fields(node, Set.of("mode", "startDate", "date", "durationDays"), true);
        try {
            ExpiryMode mode = ExpiryMode.valueOf(JsonInput.text(node, "mode", 30, false));
            if (mode == ExpiryMode.FIXED_DATE) {
                if (node.has("durationDays"))
                    throw JsonInput.invalid();
                LocalDate start = LocalDate.parse(JsonInput.text(node, "startDate", 10, false));
                LocalDate end = LocalDate.parse(JsonInput.text(node, "date", 10, false));
                if (start.getYear() < 1 || end.getYear() > 9999 || end.isBefore(start))
                    throw JsonInput.invalid();
                return new ExpiryRule(mode, start, end, null);
            }
            if (node.has("startDate") || node.has("date") || !node.has("durationDays")
                    || !node.get("durationDays").isIntegralNumber()
                    || !node.get("durationDays").canConvertToInt() || node.get("durationDays").intValue() < 1)
                throw JsonInput.invalid();
            return new ExpiryRule(mode, null, null, node.get("durationDays").intValue());
        } catch (IllegalArgumentException | java.time.DateTimeException error) {
            throw JsonInput.invalid();
        }
    }
}
