package com.devin.ontology.identity;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.security.CurrentIdentity;
import com.devin.ontology.security.PublicOrigin;
import com.devin.ontology.security.SessionRevocationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/** 管理员账号生命周期与一次性密码链接的事务边界。 */
@Service
public class AccountLifecycleService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong NEXT_TOKEN_CLEANUP_AT_MILLIS = new AtomicLong();
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SYSTEM_ADMIN_LOCK_KEY = "ontology.system-admin-state";
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 1024;

    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final CurrentIdentity currentIdentity;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService revocationService;
    private final String publicOrigin;

    public AccountLifecycleService(final JdbcTemplate jdbc, final TransactionOperations transactions,
            final CurrentIdentity currentIdentity, final PasswordEncoder passwordEncoder,
            final SessionRevocationService revocationService,
            @Value("${ontology.security.public-origin:https://ontology.invalid}") final String publicOrigin) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.currentIdentity = currentIdentity;
        this.passwordEncoder = passwordEncoder;
        this.revocationService = revocationService;
        this.publicOrigin = PublicOrigin.parse(publicOrigin);
    }

    public AccountPage listUsers(final int page, final int size) {
        requireSystemAdmin();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        List<AccountUserView> users = jdbc.query(
                "SELECT id, username, display_name, system_admin, enabled, password_activated, create_time "
                        + "FROM ontology.og_user ORDER BY create_time DESC, id LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AccountUserView(rs.getString("id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getBoolean("system_admin"), rs.getBoolean("enabled"),
                        rs.getBoolean("password_activated"), rs.getTimestamp("create_time").toLocalDateTime()),
                size, page * size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_user", Long.class);
        return new AccountPage(users, page, size, total == null ? 0 : total);
    }

    public CreatedAccount createPendingUser(final CreateAccountRequest request) {
        InternalUser actor = requireSystemAdmin();
        validateAccount(request);
        return transactions.execute(status -> {
            String userId = UUID.randomUUID().toString();
            try {
                // 管理启用状态独立于本地密码激活；激活链接不会覆盖管理员之后的停用决定。
                jdbc.update("INSERT INTO ontology.og_user(id, username, display_name, system_admin, enabled, "
                                + "password_activated, auth_version) VALUES (?, ?, ?, FALSE, TRUE, FALSE, 0)",
                        userId, request.username().strip(), request.displayName().strip());
            } catch (DuplicateKeyException duplicate) {
                throw new OntologyApiException(ApiErrorCode.ACCOUNT_USERNAME_CONFLICT, duplicate);
            }
            String rawToken = issueToken(userId, "ACTIVATE", actor.id(), 24);
            audit(actor.id(), "ACCOUNT_ACTIVATION_LINK_ISSUED", userId);
            return new CreatedAccount(userView(userId), link("activate", rawToken));
        });
    }

    public AccountUserView setEnabled(final String targetUserId, final boolean enabled) {
        requireSystemAdmin();
        return transactions.execute(status -> {
            // 所有系统管理员启停动作先取同一事务锁，避免两个管理员并发把最后账号同时停用。
            jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",
                    (ResultSetExtractor<Void>) rs -> { rs.next(); return null; }, SYSTEM_ADMIN_LOCK_KEY);
            InternalUser actor = requireSystemAdmin();
            lockUser(targetUserId);
            List<Boolean> enabledValues = jdbc.query("SELECT enabled FROM ontology.og_user WHERE id = ?",
                    (rs, rowNum) -> rs.getBoolean(1), targetUserId);
            Boolean prior = enabledValues.stream().findFirst().orElse(null);
            if (prior == null) throw notFound();
            if (prior != enabled) {
                if (!enabled && isLastEnabledSystemAdmin(targetUserId)) {
                    throw new OntologyApiException(ApiErrorCode.ACCOUNT_LAST_ADMIN);
                }
                jdbc.update("UPDATE ontology.og_user SET enabled = ?, update_time = NOW() WHERE id = ?",
                        enabled, targetUserId);
                if (!enabled) revocationService.revokeAllSessions(actor, targetUserId);
                audit(actor.id(), enabled ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED", targetUserId);
            }
            return userView(targetUserId);
        });
    }

    public String issuePasswordResetLink(final String targetUserId) {
        InternalUser actor = requireSystemAdmin();
        return transactions.execute(status -> {
            lockUser(targetUserId);
            String rawToken = issueToken(targetUserId, "RESET_PASSWORD", actor.id(), 1);
            audit(actor.id(), "ACCOUNT_PASSWORD_RESET_LINK_ISSUED", targetUserId);
            return link("reset-password", rawToken);
        });
    }

    public String issueActivationLink(final String targetUserId) {
        InternalUser actor = requireSystemAdmin();
        return transactions.execute(status -> {
            InternalUser target = lockUser(targetUserId);
            if (target.passwordActivated()) {
                throw new OntologyApiException(ApiErrorCode.ACCOUNT_ALREADY_ACTIVATED);
            }
            String rawToken = issueToken(targetUserId, "ACTIVATE", actor.id(), 24);
            audit(actor.id(), "ACCOUNT_ACTIVATION_LINK_ISSUED", targetUserId);
            return link("activate", rawToken);
        });
    }

    /** 只读检查不消费 token，避免用户打开链接时凭证被提前作废。 */
    public ActionTokenInspection inspectActionToken(final String token) {
        cleanupExpiredTokensOccasionally();
        String digest = tokenDigest(token);
        List<String> purposes = jdbc.query(
                "SELECT purpose FROM ontology.og_account_action_token WHERE token_digest = ? "
                        + "AND consumed_at IS NULL AND expires_at > NOW()",
                (rs, rowNum) -> rs.getString(1), digest);
        return purposes.isEmpty() ? new ActionTokenInspection(null, false)
                : new ActionTokenInspection(purposes.get(0), true);
    }

    public void activate(final String token, final String newPassword) {
        validatePassword(newPassword);
        transactions.executeWithoutResult(status -> {
            String userId = lockTokenOwner(token, "ACTIVATE");
            InternalUser user = lockUser(userId);
            if (user.passwordActivated()) throw invalidToken();
            consumeToken(token, "ACTIVATE");
            jdbc.update("INSERT INTO ontology.og_password_credential(user_id, password_hash) VALUES (?, ?) "
                            + "ON CONFLICT (user_id) DO UPDATE SET password_hash = EXCLUDED.password_hash",
                    userId, passwordEncoder.encode(newPassword));
            // 不修改 enabled，故停用后仍停用；Workbench OIDC 身份不受密码激活位影响。
            jdbc.update("UPDATE ontology.og_user SET password_activated = TRUE, update_time = NOW() WHERE id = ?",
                    userId);
            audit(null, "ACCOUNT_PASSWORD_ACTIVATED", userId);
        });
    }

    public void resetPassword(final String token, final String newPassword) {
        validatePassword(newPassword);
        transactions.executeWithoutResult(status -> {
            String userId = lockTokenOwner(token, "RESET_PASSWORD");
            InternalUser user = lockUser(userId);
            consumeToken(token, "RESET_PASSWORD");
            jdbc.update("INSERT INTO ontology.og_password_credential(user_id, password_hash) VALUES (?, ?) "
                            + "ON CONFLICT (user_id) DO UPDATE SET password_hash = EXCLUDED.password_hash",
                    userId, passwordEncoder.encode(newPassword));
            jdbc.update("UPDATE ontology.og_user SET password_activated = TRUE, update_time = NOW() WHERE id = ?",
                    userId);
            // 重置成功才递增安全版本并撤销该用户现存会话与 Agent 授权。
            revocationService.revokeAllSessions(user, userId);
            audit(null, "ACCOUNT_PASSWORD_RESET", userId);
        });
    }

    private InternalUser requireSystemAdmin() {
        InternalUser user = currentIdentity.requireUser();
        if (!user.systemAdmin()) throw new AccessDeniedException("需要系统管理员权限");
        return user;
    }

    private InternalUser lockUser(final String userId) {
        List<InternalUser> users = jdbc.query("SELECT id, username, display_name, system_admin, enabled, "
                        + "password_activated, auth_version FROM ontology.og_user WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> new InternalUser(rs.getString("id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getBoolean("system_admin"), rs.getBoolean("enabled"),
                        rs.getBoolean("password_activated"), rs.getLong("auth_version")), userId);
        if (users.isEmpty()) throw notFound();
        return users.get(0);
    }

    private String issueToken(final String userId, final String purpose, final String actorId,
            final int lifetimeHours) {
        cleanupExpiredTokensOccasionally();
        jdbc.update("UPDATE ontology.og_account_action_token SET consumed_at = NOW() "
                        + "WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL",
                userId, purpose);
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO ontology.og_account_action_token(token_digest, user_id, purpose, expires_at, created_by) "
                        + "VALUES (?, ?, ?, NOW() + (? * INTERVAL '1 hour'), ?)",
                digest(raw), userId, purpose, lifetimeHours, actorId);
        return raw;
    }

    private String lockTokenOwner(final String token, final String purpose) {
        String digest = tokenDigest(token);
        List<String> userIds = jdbc.query("SELECT user_id FROM ontology.og_account_action_token "
                        + "WHERE token_digest = ? AND purpose = ?",
                (rs, rowNum) -> rs.getString(1), digest, purpose);
        if (userIds.isEmpty()) throw invalidToken();
        return userIds.get(0);
    }

    private void consumeToken(final String token, final String purpose) {
        int consumed = jdbc.update("UPDATE ontology.og_account_action_token SET consumed_at = NOW() "
                        + "WHERE token_digest = ? AND purpose = ? AND consumed_at IS NULL AND expires_at > NOW()",
                digest(token), purpose);
        if (consumed != 1) throw invalidToken();
    }

    private AccountUserView userView(final String id) {
        return jdbc.query("SELECT id, username, display_name, system_admin, enabled, password_activated, create_time "
                        + "FROM ontology.og_user WHERE id = ?",
                (rs, rowNum) -> new AccountUserView(rs.getString("id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getBoolean("system_admin"), rs.getBoolean("enabled"),
                        rs.getBoolean("password_activated"), rs.getTimestamp("create_time").toLocalDateTime()), id)
                .stream().findFirst().orElseThrow(AccountLifecycleService::notFound);
    }

    private void audit(final String actorId, final String event, final String targetId) {
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES (?, ?, jsonb_build_object('targetUserId', ?))",
                event, actorId, targetId);
    }

    private String link(final String page, final String token) {
        // 凭证只放 URL fragment；浏览器不会把 fragment 发送到服务器或写入访问路径日志。
        return publicOrigin + "/ontology-management/account/" + page + "#token=" + token;
    }

    private boolean isLastEnabledSystemAdmin(final String targetUserId) {
        Boolean isAdmin = jdbc.queryForObject("SELECT system_admin FROM ontology.og_user WHERE id = ?",
                Boolean.class, targetUserId);
        if (!Boolean.TRUE.equals(isAdmin)) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_user "
                + "WHERE system_admin = TRUE AND enabled = TRUE", Integer.class);
        return count != null && count <= 1;
    }

    /** 链接记录只保留仍可使用或最近到期的一周记录，按低频小批次清理。 */
    private void cleanupExpiredTokensOccasionally() {
        long now = System.currentTimeMillis();
        long next = NEXT_TOKEN_CLEANUP_AT_MILLIS.get();
        if (now < next || !NEXT_TOKEN_CLEANUP_AT_MILLIS.compareAndSet(next, now + 600_000L)) return;
        jdbc.update("WITH expired AS (SELECT ctid FROM ontology.og_account_action_token "
                        + "WHERE expires_at < NOW() - INTERVAL '7 days' ORDER BY expires_at LIMIT 200) "
                        + "DELETE FROM ontology.og_account_action_token t USING expired e WHERE t.ctid = e.ctid");
    }

    private static void validateAccount(final CreateAccountRequest request) {
        if (request == null || request.username() == null || request.displayName() == null
                || !request.username().strip().matches("[A-Za-z0-9][A-Za-z0-9._@+-]{1,119}")
                || request.displayName().isBlank() || request.displayName().strip().length() > 200) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
    }

    private static void validatePassword(final String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH
                || password.chars().anyMatch(Character::isISOControl)) {
            throw new OntologyApiException(ApiErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }

    private static String tokenDigest(final String token) {
        if (token == null || token.length() != 43 || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidToken();
        }
        return digest(token);
    }

    private static String digest(final String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static OntologyApiException invalidToken() {
        return new OntologyApiException(ApiErrorCode.ACCOUNT_ACTION_INVALID);
    }

    private static OntologyApiException notFound() {
        return new OntologyApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    public record CreateAccountRequest(String username, String displayName) { }
    public record AccountPage(List<AccountUserView> items, int page, int size, long total) { }
    public record CreatedAccount(AccountUserView user, String activationUrl) { }
    public record ActionTokenInspection(String purpose, boolean valid) { }
    public record EnabledRequest(boolean enabled) { }
    public record ActionTokenRequest(String token) { }
    public record PasswordActionRequest(String token, String newPassword) { }
}
