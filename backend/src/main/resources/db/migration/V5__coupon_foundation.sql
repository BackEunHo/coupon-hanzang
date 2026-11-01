ALTER TABLE coupon_templates ADD CONSTRAINT coupon_templates_tenant_uk UNIQUE(id,merchant_id);
ALTER TABLE operators ADD CONSTRAINT operators_tenant_uk UNIQUE(id,merchant_id);
CREATE TABLE customers (
    id UUID PRIMARY KEY, merchant_id UUID NOT NULL REFERENCES merchants(id),
    phone_ciphertext VARCHAR(512), phone_lookup VARCHAR(64),
    last_activity_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL, deleted_at TIMESTAMPTZ,
    UNIQUE(merchant_id,phone_lookup), UNIQUE(id,merchant_id)
);
CREATE TABLE coupons (
    id UUID PRIMARY KEY, merchant_id UUID NOT NULL REFERENCES merchants(id), template_id UUID NOT NULL, customer_id UUID,
    title VARCHAR(100) NOT NULL, benefit VARCHAR(500) NOT NULL, notice VARCHAR(2000) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL, starts_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL, redeemed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL, issue_message_status VARCHAR(20) NOT NULL,
    code_mac VARCHAR(64), code_key_version INTEGER NOT NULL,
    consent_operator_id UUID NOT NULL, consent_checked_at TIMESTAMPTZ NOT NULL, row_version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(id,merchant_id),
    FOREIGN KEY(template_id,merchant_id) REFERENCES coupon_templates(id,merchant_id),
    FOREIGN KEY(customer_id,merchant_id) REFERENCES customers(id,merchant_id),
    FOREIGN KEY(consent_operator_id,merchant_id) REFERENCES operators(id,merchant_id),
    CHECK(expires_at>starts_at),
    CHECK(status IN ('PENDING_SEND','ACTIVE','SEND_FAILED','REDEEMED','EXPIRED','CLOSED_INACTIVE')),
    CHECK(issue_message_status IN ('QUEUED','SUBMITTED','DELIVERED','FAILED','UNKNOWN'))
);
CREATE INDEX coupons_merchant_time_idx ON coupons(merchant_id,issued_at DESC,id DESC);
CREATE INDEX coupons_merchant_status_idx ON coupons(merchant_id,status,expires_at);
CREATE INDEX coupons_customer_idx ON coupons(merchant_id,customer_id);
CREATE TABLE idempotency_records (
    merchant_id UUID NOT NULL REFERENCES merchants(id), operation VARCHAR(40) NOT NULL, request_key UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL, result_id UUID, created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(merchant_id,operation,request_key),
    FOREIGN KEY(result_id,merchant_id) REFERENCES coupons(id,merchant_id)
);
CREATE TABLE monthly_message_usage (
    merchant_id UUID NOT NULL REFERENCES merchants(id), month_kst DATE NOT NULL,
    reserved_count INTEGER NOT NULL DEFAULT 0 CHECK(reserved_count>=0), settled_count INTEGER NOT NULL DEFAULT 0 CHECK(settled_count>=0),
    PRIMARY KEY(merchant_id,month_kst), CHECK(EXTRACT(DAY FROM month_kst)=1)
);
CREATE TABLE message_outbox (
    id UUID PRIMARY KEY, merchant_id UUID NOT NULL REFERENCES merchants(id), coupon_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL, recipient_ciphertext TEXT, payload_ciphertext TEXT, state VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, key_version INTEGER NOT NULL,
    FOREIGN KEY(coupon_id,merchant_id) REFERENCES coupons(id,merchant_id)
);
CREATE INDEX message_outbox_pending_idx ON message_outbox(state,created_at);
