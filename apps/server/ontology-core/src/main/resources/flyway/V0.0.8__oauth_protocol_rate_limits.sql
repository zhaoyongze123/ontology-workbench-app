-- OAuth 请求桶只保存不可逆键散列，不保存客户端标识、来源地址或任何协议凭证原文。
CREATE TABLE ontology.og_credential_request_bucket (
    credential_type VARCHAR(32) NOT NULL CHECK (credential_type IN (
        'OAUTH_AUTHORIZE', 'OAUTH_TOKEN', 'OAUTH_REVOKE', 'ACCOUNT_ACTIVATE', 'ACCOUNT_RESET'
    )),
    bucket_dimension VARCHAR(16) NOT NULL CHECK (bucket_dimension IN ('SOURCE', 'CLIENT')),
    key_hash BYTEA NOT NULL CHECK (octet_length(key_hash) = 32),
    request_count INTEGER NOT NULL CHECK (request_count > 0),
    window_started_at TIMESTAMPTZ NOT NULL,
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (credential_type, bucket_dimension, key_hash)
);

CREATE INDEX idx_og_credential_request_bucket_updated
    ON ontology.og_credential_request_bucket(updated_at);
