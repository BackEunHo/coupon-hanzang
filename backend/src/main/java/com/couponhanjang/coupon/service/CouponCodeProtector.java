package com.couponhanjang.coupon.service;

import com.couponhanjang.common.crypto.SecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

@Component
public class CouponCodeProtector {
    private final SecretCipher cipher;
    private final SecureRandom random = new SecureRandom();

    public CouponCodeProtector(@Value("${app.coupon-code.encryption-key}") String encryption,
            @Value("${app.coupon-code.mac-key}") String key) {
        cipher = new SecretCipher(encryption, key);
    }

    public record GeneratedCode(String plaintext, String mac) {
        @Override
        public String toString() {
            return "GeneratedCode[REDACTED]";
        }
    }

    public GeneratedCode generate(UUID couponId) {
        String value = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        return new GeneratedCode(value, cipher.mac(couponId + ":" + value));
    }

    public String encryptPayload(String payload) {
        return cipher.encrypt(payload);
    }

    public String decryptPayload(String encrypted) {
        return cipher.decrypt(encrypted);
    }

    public String redemptionFingerprint(UUID couponId, String code) {
        return cipher.mac("REDEEM_COUPON:" + couponId + ":" + code);
    }

    public boolean verify(UUID couponId, String plain, String mac) {
        return plain != null && plain.matches("[0-9]{6}") && mac != null
                && MessageDigest.isEqual(
                        cipher.mac(couponId + ":" + plain).getBytes(StandardCharsets.US_ASCII),
                        mac.getBytes(StandardCharsets.US_ASCII));
    }
}
