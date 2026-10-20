package com.devin.ontology.oauth;

import com.devin.ontology.security.LoginUserPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/** 通过令牌摘要恢复 Agent 主体，并在每次 API 请求时重新核对账号和授权状态。 */
@Service
public class AgentAccessTokenService {
    private final JdbcTemplate jdbc;

    public AgentAccessTokenService(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Authentication authenticate(final String token) {
        if (token == null || token.length() < 32 || token.length() > 256) return null;
        String digest = digest(token);
        return jdbc.query("SELECT u.id, u.username, u.display_name, u.system_admin, u.auth_version, "
                        + "a.auth_version AS grant_auth_version, a.login_at, a.scopes "
                        + "FROM ontology.og_app_authorization a "
                        + "JOIN ontology.og_app_client c ON c.id = a.client_record_id AND c.enabled = TRUE "
                        + "JOIN ontology.og_user u ON u.id = a.user_id AND u.enabled = TRUE "
                        + "WHERE a.access_token_digest = ? AND a.access_active = TRUE "
                        + "AND a.revoked_at IS NULL AND a.access_expires_at > NOW() "
                        + "AND a.absolute_expires_at > NOW() AND a.auth_version = u.auth_version",
                (rs, rowNum) -> {
                    LoginUserPrincipal principal = new LoginUserPrincipal(rs.getString("id"),
                            rs.getString("username"), rs.getString("display_name"),
                            rs.getBoolean("system_admin"), rs.getLong("auth_version"),
                            rs.getTimestamp("login_at").toInstant().toEpochMilli());
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (principal.systemAdmin()) authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
                    String scopes = rs.getString("scopes");
                    if (scopes != null && !scopes.isBlank()) {
                        for (String scope : scopes.trim().split("\\s+")) {
                            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                        }
                    }
                    return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
                }, digest).stream().findFirst().orElse(null);
    }

    private String digest(final String token) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 缺少 SHA-256", impossible);
        }
    }
}
