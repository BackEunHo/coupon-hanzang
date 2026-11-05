package com.couponhanjang.auth.security;

import com.couponhanjang.integration.solapi.SolapiProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

final class SolapiWebhookAuthenticationFilter extends OncePerRequestFilter {
    private final SolapiProperties settings;

    SolapiWebhookAuthenticationFilter(SolapiProperties settings) {
        this.settings = settings;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String secret = settings.getWebhookSecret(), supplied = request.getHeader("X-Solapi-Secret");
        if (secret.isBlank() || supplied == null || !supplied.matches("[0-9a-fA-F]{40}")
                || !matches(secret, supplied)) {
            ApiErrorWriter.write(response, 403, "WEBHOOK_UNAUTHORIZED", "Webhook authentication failed");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matches(String secret, String supplied) {
        try {
            byte[] expected = MessageDigest.getInstance("SHA-1").digest(secret.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(supplied));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
