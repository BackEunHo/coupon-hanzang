package com.couponhanjang.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String phone, @NotBlank String password) {
}
