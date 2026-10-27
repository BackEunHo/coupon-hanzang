package com.couponhanjang.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(@NotBlank @Size(max = 100) String merchantName,
        @NotBlank String phone,
        @NotBlank @Size(min = 8, max = 128) String password) {
}
