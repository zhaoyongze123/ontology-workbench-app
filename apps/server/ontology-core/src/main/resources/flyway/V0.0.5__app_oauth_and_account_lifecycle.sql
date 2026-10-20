-- Workbench App 的 OAuth 授权与管理员开通/重置凭证。
-- 所有令牌和一次性链接只保存 SHA-256 摘要，不保存可直接使用的原值。
CREATE TABLE ontology.og_app_client (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(120) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(512) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    redirect_uri VARCHAR(2048) NOT NULL,
    scopes VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(36) REFERENCES ontology.og_user(id) ON DELETE SET NULL,
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE ontology.og_app_authorization (
    id VARCHAR(36) PRIMARY KEY,
    client_record_id VARCHAR(36) NOT NULL REFERENCES ontology.og_app_client(id) ON DELETE RESTRICT,
    user_id VARCHAR(36) NOT NULL REFERENCES ontology.og_user(id) ON DELETE CASCADE,
    auth_version BIGINT NOT NULL,
    login_at TIMESTAMP NOT NULL,
    absolute_expires_at TIMESTAMP NOT NULL,
    scopes VARCHAR(500) NOT NULL,
    authorization_request_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    code_digest CHAR(64),
    code_issued_at TIMESTAMP,
    code_expires_at TIMESTAMP,
    code_used_at TIMESTAMP,
    access_token_digest CHAR(64),
    access_issued_at TIMESTAMP,
    access_expires_at TIMESTAMP,
    access_active BOOLEAN NOT NULL DEFAULT FALSE,
    refresh_token_digest CHAR(64),
    refresh_issued_at TIMESTAMP,
    refresh_expires_at TIMESTAMP,
    revoked_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (absolute_expires_at > login_at)
);
CREATE UNIQUE INDEX uk_og_app_authorization_code_digest
    ON ontology.og_app_authorization(code_digest) WHERE code_digest IS NOT NULL;
CREATE UNIQUE INDEX uk_og_app_authorization_access_digest
    ON ontology.og_app_authorization(access_token_digest) WHERE access_token_digest IS NOT NULL;
CREATE UNIQUE INDEX uk_og_app_authorization_refresh_digest
    ON ontology.og_app_authorization(refresh_token_digest) WHERE refresh_token_digest IS NOT NULL;
CREATE INDEX idx_og_app_authorization_user
    ON ontology.og_app_authorization(user_id, create_time DESC);
CREATE INDEX idx_og_app_authorization_expiry
    ON ontology.og_app_authorization(absolute_expires_at);

-- 轮换后的刷新令牌摘要保留为重用检测墓碑，绝不保留刷新令牌原值。
CREATE TABLE ontology.og_app_refresh_token_history (
    token_digest CHAR(64) PRIMARY KEY,
    authorization_id VARCHAR(36) NOT NULL REFERENCES ontology.og_app_authorization(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REPLACED', 'REVOKED')),
    replacement_digest CHAR(64),
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    replaced_at TIMESTAMP
);
CREATE INDEX idx_og_app_refresh_token_authorization
    ON ontology.og_app_refresh_token_history(authorization_id, status);

CREATE TABLE ontology.og_app_authorization_consent (
    client_record_id VARCHAR(36) NOT NULL REFERENCES ontology.og_app_client(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES ontology.og_user(id) ON DELETE CASCADE,
    scopes VARCHAR(500) NOT NULL,
    update_time TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (client_record_id, user_id)
);

CREATE TABLE ontology.og_account_action_token (
    token_digest CHAR(64) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES ontology.og_user(id) ON DELETE CASCADE,
    purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('ACTIVATE', 'RESET_PASSWORD')),
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_by VARCHAR(36) REFERENCES ontology.og_user(id) ON DELETE SET NULL,
    create_time TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_og_account_action_target
    ON ontology.og_account_action_token(user_id, purpose, expires_at);

CREATE INDEX idx_og_audit_event_type_created
    ON ontology.og_audit_event(event_type, create_time DESC);
