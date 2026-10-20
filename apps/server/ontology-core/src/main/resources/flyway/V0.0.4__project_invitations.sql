CREATE TABLE ontology.og_project_invitation (
    id UUID PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES ontology.og_organization(id) ON DELETE CASCADE,
    inviter_user_id VARCHAR(36) REFERENCES ontology.og_user(id) ON DELETE SET NULL,
    inviter_role VARCHAR(16) CHECK (inviter_role IN ('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')),
    inviter_system_admin BOOLEAN NOT NULL DEFAULT FALSE,
    target_user_id VARCHAR(36) NOT NULL REFERENCES ontology.og_user(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER')),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    created_time TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_time TIMESTAMP NOT NULL,
    responded_time TIMESTAMP,
    CHECK (expires_time > created_time)
);

CREATE UNIQUE INDEX uk_og_project_invitation_pending_target
    ON ontology.og_project_invitation(project_id, target_user_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_og_project_invitation_target_status
    ON ontology.og_project_invitation(target_user_id, status, expires_time);
