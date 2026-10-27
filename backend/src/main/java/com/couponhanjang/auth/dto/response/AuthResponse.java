package com.couponhanjang.auth.dto.response;

import com.couponhanjang.auth.service.AuthService;

import java.util.UUID;

public record AuthResponse(UUID operatorId, UUID merchantId, String merchantName,
        String phone, boolean phoneVerified) {
    public static AuthResponse from(AuthService.SignedIn signedIn) {
        return new AuthResponse(signedIn.operatorId(), signedIn.merchantId(), signedIn.merchantName(),
                signedIn.phone(), signedIn.phoneVerified());
    }
}
