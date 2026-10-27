package com.couponhanjang.auth.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

public record AuthPrincipal(UUID operatorId, long credentialVersion, Instant authenticatedAt)
        implements Principal, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return operatorId.toString();
    }
}
