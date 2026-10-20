package com.devin.ontology.security;

import com.devin.ontology.identity.InternalUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 递增身份版本使该账号全部现存会话在下一次请求时失效，并在同一事务记录审计事实。 */
@Service
public class SessionRevocationService {
    private final JdbcTemplate jdbcTemplate;

    public SessionRevocationService(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void revokeAllSessions(final InternalUser actor) {
        int updated = jdbcTemplate.update("UPDATE ontology.og_user "
                + "SET auth_version = auth_version + 1, update_time = NOW() WHERE id = ?", actor.id());
        if (updated != 1) {
            throw new IllegalStateException("当前用户不存在，无法撤销会话");
        }
        jdbcTemplate.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES ('USER_SESSIONS_REVOKED', ?, jsonb_build_object('targetUserId', ?))",
                actor.id(), actor.id());
    }
}
