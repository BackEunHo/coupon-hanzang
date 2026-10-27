package com.couponhanjang.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WithdrawalRequest(@NotBlank String password) {
}
