package com.couponhanjang.common.api;

import com.couponhanjang.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;
import java.util.UUID;

public final class JsonInput {
    private JsonInput() {
    }

    public static void fields(JsonNode node, Set<String> allowed, boolean nonempty) {
        if (node == null || !node.isObject() || (nonempty && node.isEmpty())) {
            throw invalid();
        }
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name))
                throw invalid();
        });
    }

    public static String text(JsonNode node, String name, int max, boolean blankAllowed) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.textValue().length() > max
                || (!blankAllowed && value.textValue().isBlank())) {
            throw invalid();
        }
        return value.textValue();
    }

    public static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value))
                throw invalid();
            return id;
        } catch (IllegalArgumentException error) {
            throw invalid();
        }
    }

    public static ApiException invalid() {
        return new ApiException(400, "INVALID_REQUEST", "Invalid request body");
    }
}
