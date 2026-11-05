ALTER TABLE coupons ADD COLUMN current_outbox_id UUID;
ALTER TABLE coupons ADD COLUMN expected_message_type VARCHAR(3);
ALTER TABLE coupons ADD COLUMN actual_message_type VARCHAR(3);
ALTER TABLE coupons ADD COLUMN message_byte_length INTEGER;
ALTER TABLE message_outbox ADD COLUMN payload_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE message_outbox ADD COLUMN dispatch_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE message_outbox ADD COLUMN expected_type VARCHAR(3);
ALTER TABLE message_outbox ADD COLUMN byte_length INTEGER;
ALTER TABLE message_outbox ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE message_outbox ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE message_outbox ADD COLUMN lease_token UUID;
ALTER TABLE message_outbox ADD COLUMN lease_until TIMESTAMPTZ;
ALTER TABLE message_outbox ADD COLUMN reservation_month DATE;
ALTER TABLE message_outbox ADD COLUMN settlement_status VARCHAR(12) NOT NULL DEFAULT 'RESERVED';
UPDATE message_outbox SET reservation_month = date_trunc('month', created_at AT TIME ZONE 'Asia/Seoul')::date;
ALTER TABLE message_outbox ALTER COLUMN reservation_month SET NOT NULL;
ALTER TABLE coupons ADD CONSTRAINT coupons_current_outbox_fk FOREIGN KEY(current_outbox_id) REFERENCES message_outbox(id);
CREATE UNIQUE INDEX outbox_one_live_issue ON message_outbox(coupon_id)
    WHERE dispatch_enabled AND kind='COUPON_ISSUED' AND state IN ('QUEUED','PROCESSING','SUBMITTED','UNKNOWN');
CREATE TABLE message_deliveries (
    id UUID PRIMARY KEY REFERENCES message_outbox(id),
    provider_message_id VARCHAR(100) UNIQUE, provider_group_id VARCHAR(100),
    state VARCHAR(20) NOT NULL, status_code VARCHAR(40), actual_type VARCHAR(3),
    requested_at TIMESTAMPTZ NOT NULL, reported_at TIMESTAMPTZ,
    next_check_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actual_cost_krw NUMERIC(12,2)
);
CREATE TABLE message_inbox (
    id UUID PRIMARY KEY, event_key VARCHAR(64) NOT NULL UNIQUE,
    provider_message_id VARCHAR(100) NOT NULL, attempt_id UUID,
    status_code VARCHAR(10) NOT NULL, actual_type VARCHAR(3), reported_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL, processed_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX message_inbox_pending_idx ON message_inbox(next_attempt_at) WHERE processed_at IS NULL;
CREATE TABLE message_retry_requests (
    merchant_id UUID NOT NULL REFERENCES merchants(id), request_key UUID NOT NULL,
    coupon_id UUID NOT NULL, outbox_id UUID REFERENCES message_outbox(id), created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(merchant_id,request_key), FOREIGN KEY(coupon_id,merchant_id) REFERENCES coupons(id,merchant_id)
);
