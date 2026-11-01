CREATE TABLE coupon_templates (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    title VARCHAR(100) NOT NULL,
    benefit VARCHAR(500) NOT NULL,
    notice VARCHAR(2000) NOT NULL,
    expiry_mode VARCHAR(20) NOT NULL,
    fixed_start_date DATE,
    fixed_end_date DATE,
    relative_duration_days INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    CHECK ((expiry_mode = 'FIXED_DATE' AND fixed_start_date IS NOT NULL AND fixed_end_date >= fixed_start_date AND fixed_end_date IS NOT NULL AND relative_duration_days IS NULL)
        OR (expiry_mode = 'RELATIVE_DAYS' AND relative_duration_days > 0 AND relative_duration_days IS NOT NULL AND fixed_start_date IS NULL AND fixed_end_date IS NULL))
);
CREATE INDEX coupon_templates_merchant_idx ON coupon_templates(merchant_id, deleted_at, created_at DESC);
