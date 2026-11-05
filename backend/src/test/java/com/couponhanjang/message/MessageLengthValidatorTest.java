package com.couponhanjang.message;

import com.couponhanjang.message.service.MessageLengthValidator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MessageLengthValidatorTest {
    private final MessageLengthValidator validator = new MessageLengthValidator();

    @Test
    void smsAndLmsBoundariesUseEncodedBytes() {
        assertThat(validator.inspect("a".repeat(90)).expectedMessageType()).isEqualTo("SMS");
        assertThat(validator.inspect("a".repeat(91)).expectedMessageType()).isEqualTo("LMS");
        assertThat(validator.inspect("가".repeat(45)).byteLength()).isEqualTo(90);
        assertThat(validator.inspect("가".repeat(1000)).sendable()).isTrue();
        assertThat(validator.inspect("가".repeat(1000) + "a").validationErrors()).contains("MESSAGE_TOO_LONG");
        assertThat(validator.inspect("가\n a").byteLength()).isEqualTo(5);
    }

    @Test
    void unsupportedCharactersAreNotSilentlyReplaced() {
        var result = validator.inspect("쿠폰 😀");
        assertThat(result.sendable()).isFalse();
        assertThat(result.byteLength()).isNull();
        assertThat(result.validationErrors()).contains("UNSUPPORTED_MESSAGE_CHARACTERS");
        assertThat(validator.inspect("").sendable()).isFalse();
        assertThat(validator.inspect("hello\u0000world").sendable()).isFalse();
    }
}
