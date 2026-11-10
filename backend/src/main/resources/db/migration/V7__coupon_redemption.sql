ALTER TABLE coupons ADD COLUMN redeem_failed_attempts INTEGER NOT NULL DEFAULT 0
    CHECK (redeem_failed_attempts >= 0);
ALTER TABLE coupons ADD COLUMN redeem_locked_until TIMESTAMPTZ;

CREATE TABLE coupon_redemption_requests (
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    request_key UUID NOT NULL,
    coupon_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    http_status INTEGER,
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (merchant_id, request_key),
    FOREIGN KEY (coupon_id, merchant_id) REFERENCES coupons(id, merchant_id),
    CHECK (http_status IS NULL OR http_status IN (200, 409, 422, 429)),
    CHECK (http_status IS NULL OR (http_status = 200 AND error_code IS NULL)
           OR (http_status <> 200 AND error_code IS NOT NULL))
);
