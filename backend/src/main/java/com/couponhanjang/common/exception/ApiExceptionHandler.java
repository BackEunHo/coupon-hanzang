package com.couponhanjang.common.exception;


import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException error) {
        return ResponseEntity.status(error.status()).body(new ApiError(error.code(), error.getMessage(), UUID.randomUUID()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalidRequest(Exception error) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Invalid request body", UUID.randomUUID()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("PHONE_ALREADY_REGISTERED", "This phone is already registered", UUID.randomUUID()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> staleAccount(ObjectOptimisticLockingFailureException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("ACCOUNT_CONFLICT", "Account changed; retry the request", UUID.randomUUID()));
    }

    record ApiError(String code, String message, UUID requestId) { }
}
