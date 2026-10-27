CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    withdrawn_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE operators (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    phone_ciphertext VARCHAR(512),
    phone_lookup VARCHAR(64) UNIQUE,
    password_hash VARCHAR(255),
    phone_verified_at TIMESTAMP WITH TIME ZONE,
    credential_version BIGINT NOT NULL,
    failed_login_attempts INTEGER NOT NULL,
    login_locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    withdrawn_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX operators_merchant_id_idx ON operators(merchant_id);
