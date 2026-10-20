-- 只持久化不可逆键散列和失败计数，不存用户名、IP 原文或密码。
CREATE TABLE ontology.og_login_attempt_bucket (
    bucket_type VARCHAR(16) NOT NULL CHECK (bucket_type IN ('ACCOUNT', 'IP')),
    key_hash BYTEA NOT NULL CHECK (octet_length(key_hash) = 32),
    failure_count INTEGER NOT NULL CHECK (failure_count > 0),
    window_started_at TIMESTAMPTZ NOT NULL,
    cooldown_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (bucket_type, key_hash)
);
CREATE INDEX idx_og_login_attempt_bucket_updated
    ON ontology.og_login_attempt_bucket(updated_at);
