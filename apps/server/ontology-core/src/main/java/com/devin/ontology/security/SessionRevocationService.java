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
        revokeAllSessions(actor, actor.id());
    }

    /** 管理员为目标账号改密或停用时，在同一事务撤销目标账号全部会话与 Agent 授权。 */
    @Transactional
    public void revokeAllSessions(final InternalUser actor, final String targetUserId) {
        // 账号行锁与 OAuth 刷新/撤销共用同一把锁，避免并发刷新越过安全版本更新。
        int updated = jdbcTemplate.update("UPDATE ontology.og_user "
                + "SET auth_version = auth_version + 1, update_time = NOW() WHERE id = ?", targetUserId);
        if (updated != 1) {
            throw new IllegalStateException("当前用户不存在，无法撤销会话");
        }
        // 同一事务撤销 Agent 凭证，auth_version 检查和授权记录状态保持一致。
        jdbcTemplate.update("UPDATE ontology.og_app_authorization SET revoked_at = COALESCE(revoked_at, NOW()), "
                + "access_active = FALSE, update_time = NOW() WHERE user_id = ?", actor.id());
        jdbcTemplate.update("UPDATE ontology.og_app_refresh_token_history h SET status = 'REVOKED' "
                + "FROM ontology.og_app_authorization a WHERE h.authorization_id = a.id "
                + "AND a.user_id = ? AND h.status = 'ACTIVE'", targetUserId);
        jdbcTemplate.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES ('USER_SESSIONS_REVOKED', ?, jsonb_build_object('targetUserId', ?))",
                actor.id(), targetUserId);
    }
}
