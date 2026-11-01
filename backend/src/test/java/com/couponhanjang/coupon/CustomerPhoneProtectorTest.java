package com.couponhanjang.coupon;

import com.couponhanjang.common.crypto.SecretCipher;
import com.couponhanjang.coupon.service.CouponCodeProtector;
import com.couponhanjang.coupon.service.CustomerPhoneProtector;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CustomerPhoneProtectorTest {
    private final String encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
    private final String lookupKey = Base64.getEncoder()
            .encodeToString("12345678901234567890123456789012".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

    @Test
    void ciphertextIsRandomizedAndTamperingCannotRevealPhone() {
        SecretCipher cipher = new SecretCipher(encryptionKey, lookupKey);
        String first = cipher.encrypt("01012345678");
        assertThat(cipher.encrypt("01012345678")).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo("01012345678");
        byte[] tampered = Base64.getDecoder().decode(first);
        tampered[tampered.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(tampered)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void merchantSpecificLookupAndPhoneNormalizationAgree() {
        CustomerPhoneProtector phones = new CustomerPhoneProtector(encryptionKey, lookupKey);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertThat(phones.normalize("010-1234-5678")).isEqualTo("01012345678");
        assertThat(phones.lookup(first, "01012345678"))
                .isNotEqualTo(phones.lookup(second, "01012345678"));
    }

    @Test
    void couponCodeIsBoundToCouponAndDoesNotAppearInDiagnosticText() {
        CouponCodeProtector codes = new CouponCodeProtector(encryptionKey, lookupKey);
        UUID id = UUID.randomUUID();
        var generated = codes.generate(id);
        assertThat(generated.plaintext()).matches("[0-9]{6}");
        assertThat(codes.verify(id, generated.plaintext(), generated.mac())).isTrue();
        assertThat(codes.verify(UUID.randomUUID(), generated.plaintext(), generated.mac())).isFalse();
        assertThat(generated.toString()).doesNotContain(generated.plaintext());
    }
}
