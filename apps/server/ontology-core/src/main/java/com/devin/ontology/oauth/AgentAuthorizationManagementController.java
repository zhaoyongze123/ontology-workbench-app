package com.devin.ontology.oauth;

import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.CurrentIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 网页用户只能读取或取消自己的 App 授权，列表接口永不返回任何令牌原值。 */
@RestController
@Profile("multiuser")
@RequestMapping("/auth/app-authorizations")
public class AgentAuthorizationManagementController {
    private final CurrentIdentity currentIdentity;
    private final JdbcTemplate jdbc;
    private final DigestOAuth2AuthorizationService authorizations;

    public AgentAuthorizationManagementController(final CurrentIdentity currentIdentity,
            final JdbcTemplate jdbc, final DigestOAuth2AuthorizationService authorizations) {
        this.currentIdentity = currentIdentity;
        this.jdbc = jdbc;
        this.authorizations = authorizations;
    }

    @GetMapping
    public List<AuthorizationView> list() {
        InternalUser user = currentIdentity.requireUser();
        return jdbc.query("SELECT a.id, c.display_name, a.scopes, a.login_at, a.absolute_expires_at, a.revoked_at "
                        + "FROM ontology.og_app_authorization a JOIN ontology.og_app_client c "
                        + "ON c.id = a.client_record_id WHERE a.user_id = ? ORDER BY a.login_at DESC",
                (rs, rowNum) -> new AuthorizationView(rs.getString("id"), rs.getString("display_name"),
                        rs.getString("scopes"), rs.getTimestamp("login_at").toInstant(),
                        rs.getTimestamp("absolute_expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()),
                user.id());
    }

    @DeleteMapping("/{authorizationId}")
    @Transactional
    public ResponseEntity<Map<String, String>> revoke(@PathVariable final String authorizationId) {
        InternalUser user = currentIdentity.requireUser();
        if (!authorizations.revokeForUser(authorizationId, user.id())) {
            // 对不存在和不属于当前用户的授权统一返回 404，避免泄露其他用户的资源是否存在。
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        return ResponseEntity.ok(Map.of("status", "revoked"));
    }

    public record AuthorizationView(String id, String applicationName, String scopes, Instant authorizedAt,
            Instant absoluteExpiresAt, Instant revokedAt) { }
}
