package com.couponhanjang.integration.solapi;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

public final class SolapiAuthSigner {
    private SolapiAuthSigner() {
    }

    public static String sign(String key, String secret, String date, String salt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
            return "HMAC-SHA256 apiKey=" + key + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("SOLAPI signing failed", error);
        }
    }
}
