package com.couponhanjang.message.service;

import com.couponhanjang.common.exception.ApiException;
import org.springframework.stereotype.Component;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.List;

@Component
public class MessageLengthValidator {
    public record Inspection(Integer byteLength, String expectedMessageType, boolean sendable,
            List<String> validationErrors) {
    }

    public Inspection inspect(String text) {
        if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r')) {
            return new Inspection(null, null, false, List.of("UNSUPPORTED_MESSAGE_CHARACTERS"));
        }
        try {
            int size = Charset.forName("EUC-KR").newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(text)).remaining();
            String error = size == 0 ? "MESSAGE_EMPTY" : size > 2000 ? "MESSAGE_TOO_LONG" : null;
            return new Inspection(size, size <= 90 ? "SMS" : "LMS", error == null,
                    error == null ? List.of() : List.of(error));
        } catch (CharacterCodingException error) {
            return new Inspection(null, null, false, List.of("UNSUPPORTED_MESSAGE_CHARACTERS"));
        }
    }

    public Inspection requireSendable(String text) {
        Inspection result = inspect(text);
        if (!result.sendable())
            throw new ApiException(422, result.validationErrors().get(0),
                    "Message must contain supported characters and fit within 2000 bytes");
        return result;
    }
}
