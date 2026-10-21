package com.devin.ontology.oidc;

import com.devin.ontology.identity.InternalUser;
import java.sql.PreparedStatement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

/** Workbench 外部身份映射的 PostgreSQL 实现。 */
@Repository
class JdbcWorkbenchIdentityStore implements WorkbenchIdentityStore {
    private static final String USER_COLUMNS = "u.id, u.username, u.display_name, u.system_admin, u.enabled, "
            + "u.password_activated, u.auth_version";
    private final JdbcTemplate jdbc;

    JdbcWorkbenchIdentityStore(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockExternalIdentity(final String issuer, final String subject) {
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                (PreparedStatementCallback<Void>) statement -> {
                    statement.setString(1, issuer);
                    statement.setString(2, subject);
                    statement.execute();
                    return null;
                });
    }

    @Override
    public Optional<InternalUser> findByExternalIdentity(final String issuer, final String subject) {
        return jdbc.query("SELECT " + USER_COLUMNS + " FROM ontology.og_external_identity e "
                        + "JOIN ontology.og_user u ON u.id = e.user_id WHERE e.issuer = ? AND e.subject = ?",
                (rs, rowNum) -> new InternalUser(rs.getString("id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getBoolean("system_admin"), rs.getBoolean("enabled"),
                        rs.getBoolean("password_activated"), rs.getLong("auth_version")), issuer, subject)
                .stream().findFirst();
    }

    @Override
    public Optional<InternalUser> findUserForUpdate(final String userId) {
        return jdbc.query("SELECT id, username, display_name, system_admin, enabled, password_activated, auth_version "
                        + "FROM ontology.og_user WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> new InternalUser(rs.getString("id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getBoolean("system_admin"), rs.getBoolean("enabled"),
                        rs.getBoolean("password_activated"), rs.getLong("auth_version")), userId)
                .stream().findFirst();
    }

    @Override
    public Optional<String> findExternalOwner(final String issuer, final String subject) {
        return jdbc.query("SELECT user_id FROM ontology.og_external_identity WHERE issuer = ? AND subject = ?",
                (rs, rowNum) -> rs.getString(1), issuer, subject).stream().findFirst();
    }

    @Override
    public void insertUser(final InternalUser user) {
        jdbc.update("INSERT INTO ontology.og_user(id, username, display_name, system_admin, enabled, auth_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", user.id(), user.username(), user.displayName(),
                user.systemAdmin(), user.enabled(), user.authVersion());
    }

    @Override
    public void insertExternalIdentity(final String issuer, final String subject, final String userId) {
        jdbc.update("INSERT INTO ontology.og_external_identity(issuer, subject, user_id) VALUES (?, ?, ?)",
                issuer, subject, userId);
    }

    @Override
    public void audit(final String eventType, final String actorUserId, final String issuer, final String operation) {
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES (?, ?, jsonb_build_object('issuer', ?, 'operation', ?))",
                eventType, actorUserId, issuer, operation);
    }
}
