package com.couponhanjang.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

public final class SecretCipher {
    private final byte[] encryptionKey;
    private final byte[] macKey;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(String encryptionKey, String macKey) {
        this.encryptionKey = decode(encryptionKey);
        this.macKey = decode(macKey);
    }

    private byte[] decode(String key) {
        byte[] value = Base64.getDecoder().decode(key);
        if (value.length != 32) {
            throw new IllegalArgumentException("Keys must contain 32 bytes");
        }
        return value;
    }

    public String encrypt(String plain) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] result = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Encryption failed", error);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] value = Base64.getDecoder().decode(encrypted);
            if (value.length < 28) {
                throw new IllegalArgumentException("Invalid ciphertext");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, Arrays.copyOf(value, 12)));
            return new String(cipher.doFinal(value, 12, value.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("Decryption failed", error);
        }
    }

    public String mac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("MAC failed", error);
        }
    }
}
