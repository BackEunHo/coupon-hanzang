package com.couponhanjang.message.dto;

import java.time.Instant;
import java.util.List;

public record MessagePreviewResponse(String previewText, Integer byteLength, String expectedMessageType,
        boolean sendable, List<String> validationErrors, Instant calculatedAt) {
}
