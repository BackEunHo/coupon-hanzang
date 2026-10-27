package com.couponhanjang.auth.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

final class OriginGuardFilter extends OncePerRequestFilter {
    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final String allowedOrigin;

    OriginGuardFilter(String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (UNSAFE.contains(request.getMethod()) && !allowedOrigin.equals(request.getHeader("Origin"))) {
            ApiErrorWriter.write(response, 403, "ORIGIN_DENIED", "Request origin is not allowed");
            return;
        }
        chain.doFilter(request, response);
    }
}
