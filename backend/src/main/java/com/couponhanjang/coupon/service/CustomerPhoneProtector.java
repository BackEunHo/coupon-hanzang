package com.couponhanjang.coupon.service;

import com.couponhanjang.common.crypto.SecretCipher;
import com.couponhanjang.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CustomerPhoneProtector {
    private final SecretCipher cipher;

    public CustomerPhoneProtector(@Value("${app.customer-phone.encryption-key}") String encryption,
            @Value("${app.customer-phone.lookup-key}") String lookup) {
        cipher = new SecretCipher(encryption, lookup);
    }

    public String normalize(String input) {
        if (input == null || !input.matches("^(010[0-9]{8}|010-[0-9]{4}-[0-9]{4})$")) {
            throw new ApiException(400, "INVALID_PHONE", "Enter a valid 010 mobile number");
        }
        return input.replace("-", "");
    }

    public String encrypt(String phone) {
        return cipher.encrypt(phone);
    }

    public String decrypt(String ciphertext) {
        return cipher.decrypt(ciphertext);
    }

    public String lookup(UUID merchantId, String phone) {
        return cipher.mac("customer:" + merchantId + ":" + phone);
    }

    public String fingerprint(String canonicalRequest) {
        return cipher.mac("issue-request:" + canonicalRequest);
    }
}
