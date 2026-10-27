package com.couponhanjang.common.crypto;

import com.couponhanjang.common.exception.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class PhoneProtector {
    private final byte[] encryptionKey;
    private final byte[] lookupKey;
    private final SecureRandom random = new SecureRandom();

    public PhoneProtector(@Value("${app.phone.encryption-key}") String encryptionKey,
                          @Value("${app.phone.lookup-key}") String lookupKey) {
        this.encryptionKey = decodeKey(encryptionKey);
        this.lookupKey = decodeKey(lookupKey);
    }

    private static byte[] decodeKey(String value) {
        byte[] decoded = Base64.getDecoder().decode(value);
        if (decoded.length != 32) throw new IllegalArgumentException("Phone keys must contain 32 bytes");
        return decoded;
    }

    public String normalize(String input) {
        if (input == null || !input.matches("^(010[0-9]{8}|010-[0-9]{4}-[0-9]{4})$")) {
            throw new ApiException(400, "INVALID_PHONE", "Enter a valid 010 mobile number");
        }
        return input.replace("-", "");
    }

    public String lookup(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(lookupKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(phone.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Phone lookup failed", e);
        }
    }

    public String encrypt(String phone) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(phone.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Phone encryption failed", e);
        }
    }
}
