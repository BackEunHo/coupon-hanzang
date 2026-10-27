package com.couponhanjang.auth.security;

import com.couponhanjang.auth.service.AuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

final class SessionValidationFilter extends OncePerRequestFilter {
    private final AuthService service;
    private final Clock clock;

    SessionValidationFilter(AuthService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) &&
                ("/api/v1/auth/login".equals(path) || "/api/v1/auth/signup".equals(path))) {
            chain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            boolean expired = !clock.instant().isBefore(principal.authenticatedAt().plus(Duration.ofHours(24)));
            if (expired || !service.isCurrent(principal)) {
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                ApiErrorWriter.write(response, 401, "SESSION_EXPIRED", "Authentication required");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
