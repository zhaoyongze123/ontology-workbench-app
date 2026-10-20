-- OAuth 用户确认页的随机 state 只存 SHA-256 摘要，并限制短期、一次性使用。
ALTER TABLE ontology.og_app_authorization
    ADD COLUMN consent_state_digest CHAR(64),
    ADD COLUMN consent_state_expires_at TIMESTAMP,
    ADD CONSTRAINT ck_og_app_consent_state_lifetime
        CHECK ((consent_state_digest IS NULL) = (consent_state_expires_at IS NULL));

CREATE UNIQUE INDEX uk_og_app_authorization_consent_state_digest
    ON ontology.og_app_authorization(consent_state_digest)
    WHERE consent_state_digest IS NOT NULL;
