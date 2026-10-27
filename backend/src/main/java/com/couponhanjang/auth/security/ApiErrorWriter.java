package com.couponhanjang.auth.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

final class ApiErrorWriter {
    private ApiErrorWriter() { }

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"requestId\":\"" + UUID.randomUUID() + "\"}");
    }
}
