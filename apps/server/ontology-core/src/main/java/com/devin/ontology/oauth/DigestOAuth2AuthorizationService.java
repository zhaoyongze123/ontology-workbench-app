package com.devin.ontology.oauth;

import com.devin.ontology.identity.IdentityRepository;
import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.LoginUserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 只保存授权码和令牌的 SHA-256 摘要；唯一可读原值仅短暂存在于协议内存对象中。 */
@Repository
public class DigestOAuth2AuthorizationService implements OAuth2AuthorizationService {
    private static final OAuth2TokenType AUTHORIZATION_CODE = new OAuth2TokenType(OAuth2ParameterNames.CODE);
    private static final String AUTH_REQUEST_ATTRIBUTE = OAuth2AuthorizationRequest.class.getName();
    private static final String PRINCIPAL_ATTRIBUTE = Principal.class.getName();
    private static final RowMapper<AuthorizationRow> ROW_MAPPER = (rs, rowNum) -> new AuthorizationRow(
            rs.getString("id"), rs.getString("client_record_id"), rs.getString("client_id"),
            rs.getString("user_id"), rs.getLong("auth_version"), rs.getTimestamp("login_at").toInstant(),
            rs.getTimestamp("absolute_expires_at").toInstant(), rs.getString("scopes"),
            rs.getString("authorization_request_json"), rs.getString("code_digest"),
            instant(rs.getTimestamp("code_issued_at")), instant(rs.getTimestamp("code_expires_at")),
            instant(rs.getTimestamp("code_used_at")), rs.getString("access_token_digest"),
            instant(rs.getTimestamp("access_issued_at")), instant(rs.getTimestamp("access_expires_at")),
            rs.getBoolean("access_active"), rs.getString("refresh_token_digest"),
            instant(rs.getTimestamp("refresh_issued_at")), instant(rs.getTimestamp("refresh_expires_at")),
            instant(rs.getTimestamp("revoked_at")));

    private final JdbcTemplate jdbc;
    private final RegisteredClientRepository clients;
    private final IdentityRepository identities;
    private final ObjectMapper mapper;

    public DigestOAuth2AuthorizationService(final JdbcTemplate jdbc,
            final RegisteredClientRepository clients, final IdentityRepository identities,
            final ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.identities = identities;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(final OAuth2Authorization authorization) {
        Authentication authentication = authorization.getAttribute(PRINCIPAL_ATTRIBUTE);
        LoginUserPrincipal principal = authentication != null
                && authentication.getPrincipal() instanceof LoginUserPrincipal value ? value : null;
        Instant loginAt = authorization.getAttribute(OpaqueOAuth2TokenGenerator.LOGIN_AT_ATTRIBUTE);
        if (loginAt == null && principal != null) loginAt = Instant.ofEpochMilli(principal.loginAtEpochMillis());
        Instant absoluteExpiry = authorization.getAttribute(OpaqueOAuth2TokenGenerator.ABSOLUTE_EXPIRY_ATTRIBUTE);
        if (absoluteExpiry == null && loginAt != null) {
            absoluteExpiry = loginAt.plus(OpaqueOAuth2TokenGenerator.MAX_GRANT_LIFETIME);
        }
        if (loginAt == null || absoluteExpiry == null || !absoluteExpiry.isAfter(loginAt)) {
            throw new IllegalArgumentException("OAuth 授权缺少有效的原始登录期限");
        }

        OAuth2AuthorizationRequest authorizationRequest = authorization.getAttribute(AUTH_REQUEST_ATTRIBUTE);
        String requestJson = authorizationRequest == null ? "{}" : encodeAuthorizationRequest(authorizationRequest);
        OAuth2Authorization.Token<OAuth2AuthorizationCode> code = authorization.getToken(OAuth2AuthorizationCode.class);
        OAuth2Authorization.Token<OAuth2AccessToken> access = authorization.getAccessToken();
        OAuth2Authorization.Token<OAuth2RefreshToken> refresh = authorization.getRefreshToken();
        String codeValue = code == null ? null : code.getToken().getTokenValue();
        String accessValue = access == null ? null : access.getToken().getTokenValue();
        String refreshValue = refresh == null ? null : refresh.getToken().getTokenValue();
        Instant codeUsedAt = code != null && code.isInvalidated() ? Instant.now() : null;
        boolean accessActive = access != null && access.isActive();

        String userId = principal == null ? authorization.getPrincipalName() : principal.userId();
        long authVersion = principal == null ? findAuthVersion(userId) : principal.authVersion();
        Instant finalLoginAt = loginAt;
        Instant finalAbsoluteExpiry = absoluteExpiry;
        String scopes = String.join(" ", authorization.getAuthorizedScopes().stream().sorted().toList());

        int existing = jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_app_authorization WHERE id = ?",
                Integer.class, authorization.getId());
        if (existing == 0) {
            if (principal == null || authorizationRequest == null) {
                throw new IllegalArgumentException("新授权必须来自已验证的网页登录会话");
            }
            jdbc.update("INSERT INTO ontology.og_app_authorization(id, client_record_id, user_id, auth_version, "
                            + "login_at, absolute_expires_at, scopes, authorization_request_json, code_digest, "
                            + "code_issued_at, code_expires_at, code_used_at, access_token_digest, access_issued_at, "
                            + "access_expires_at, access_active, refresh_token_digest, refresh_issued_at, "
                            + "refresh_expires_at, create_time, update_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    authorization.getId(), authorization.getRegisteredClientId(), userId, authVersion,
                    finalLoginAt, finalAbsoluteExpiry, scopes, requestJson,
                    digest(codeValue), tokenIssuedAt(code), tokenExpiresAt(code), codeUsedAt,
                    digest(accessValue), tokenIssuedAt(access), tokenExpiresAt(access), accessActive,
                    digest(refreshValue), tokenIssuedAt(refresh), tokenExpiresAt(refresh));
            if (refreshValue != null && refresh.isActive()) insertRefreshHistory(authorization.getId(), digest(refreshValue));
            return;
        }

        String oldRefreshDigest = jdbc.query("SELECT refresh_token_digest FROM ontology.og_app_authorization "
                        + "WHERE id = ? FOR UPDATE", (rs, rowNum) -> rs.getString(1), authorization.getId())
                .stream().findFirst().orElse(null);
        String newRefreshDigest = digest(refreshValue);
        if (oldRefreshDigest != null && !oldRefreshDigest.equals(newRefreshDigest)) {
            jdbc.update("UPDATE ontology.og_app_refresh_token_history SET status = 'REPLACED', "
                            + "replacement_digest = ?, replaced_at = NOW() "
                            + "WHERE token_digest = ? AND status = 'ACTIVE'",
                    newRefreshDigest, oldRefreshDigest);
        }
        jdbc.update("UPDATE ontology.og_app_authorization SET scopes = ?, "
                        + "authorization_request_json = CASE WHEN ? = '{}' THEN authorization_request_json ELSE ?::jsonb END, "
                        + "code_digest = COALESCE(?, code_digest), code_issued_at = COALESCE(?, code_issued_at), "
                        + "code_expires_at = COALESCE(?, code_expires_at), code_used_at = COALESCE(?, code_used_at), "
                        + "access_token_digest = COALESCE(?, access_token_digest), "
                        + "access_issued_at = COALESCE(?, access_issued_at), "
                        + "access_expires_at = COALESCE(?, access_expires_at), "
                        + "access_active = CASE WHEN ? THEN TRUE WHEN ? THEN FALSE ELSE access_active END, "
                        + "refresh_token_digest = COALESCE(?, refresh_token_digest), "
                        + "refresh_issued_at = COALESCE(?, refresh_issued_at), "
                        + "refresh_expires_at = COALESCE(?, refresh_expires_at), update_time = NOW() WHERE id = ?",
                scopes, requestJson, requestJson, digest(codeValue), tokenIssuedAt(code), tokenExpiresAt(code), codeUsedAt,
                digest(accessValue), tokenIssuedAt(access), tokenExpiresAt(access), accessActive,
                access != null, newRefreshDigest, tokenIssuedAt(refresh), tokenExpiresAt(refresh), authorization.getId());
        if (refreshValue != null && refresh.isActive()
                && (oldRefreshDigest == null || !oldRefreshDigest.equals(newRefreshDigest))) {
            insertRefreshHistory(authorization.getId(), newRefreshDigest);
        }
    }

    @Override
    @Transactional
    public void remove(final OAuth2Authorization authorization) {
        revoke(authorization.getId(), "APP_AUTHORIZATION_REMOVED");
    }

    @Override
    public OAuth2Authorization findById(final String id) {
        return jdbc.query(baseSelect() + " WHERE a.id = ?", ROW_MAPPER, id)
                .stream().findFirst().map(row -> build(row, null, null)).orElse(null);
    }

    @Override
    @Transactional
    public OAuth2Authorization findByToken(final String token, final OAuth2TokenType tokenType) {
        if (token == null || token.isBlank() || tokenType == null) return null;
        String tokenDigest = digest(token);
        AuthorizationRow row;
        if (AUTHORIZATION_CODE.equals(tokenType)) {
            row = queryByDigest("a.code_digest", tokenDigest);
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            row = queryByDigest("a.access_token_digest", tokenDigest);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            var history = jdbc.query("SELECT authorization_id, status FROM ontology.og_app_refresh_token_history "
                            + "WHERE token_digest = ? FOR UPDATE",
                    (rs, rowNum) -> new RefreshHistory(rs.getString("authorization_id"), rs.getString("status")),
                    tokenDigest).stream().findFirst().orElse(null);
            if (history == null) return null;
            if ("REPLACED".equals(history.status())) {
                revoke(history.authorizationId(), "APP_REFRESH_TOKEN_REUSE_DETECTED");
                return null;
            }
            if (!"ACTIVE".equals(history.status())) return null;
            row = queryByDigest("a.refresh_token_digest", tokenDigest);
        } else {
            return null;
        }
        if (row == null) return null;
        if (!isGrantUsable(row)) {
            revoke(row.id(), "APP_AUTHORIZATION_INVALIDATED");
            return null;
        }
        if (AUTHORIZATION_CODE.equals(tokenType) && row.codeUsedAt() != null) return null;
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)
                && (!row.accessActive() || row.accessExpiresAt() == null || !row.accessExpiresAt().isAfter(Instant.now()))) {
            return null;
        }
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)
                && (row.refreshExpiresAt() == null || !row.refreshExpiresAt().isAfter(Instant.now()))) {
            revoke(row.id(), "APP_REFRESH_TOKEN_EXPIRED");
            return null;
        }
        return build(row, token, tokenType);
    }

    @Transactional
    public boolean revokeForUser(final String authorizationId, final String userId) {
        int owned = jdbc.update("UPDATE ontology.og_app_authorization SET revoked_at = COALESCE(revoked_at, NOW()), "
                + "access_active = FALSE, update_time = NOW() WHERE id = ? AND user_id = ?",
                authorizationId, userId);
        if (owned == 0) return false;
        jdbc.update("UPDATE ontology.og_app_refresh_token_history SET status = 'REVOKED' "
                + "WHERE authorization_id = ? AND status = 'ACTIVE'", authorizationId);
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                + "VALUES ('APP_AUTHORIZATION_REVOKED_BY_USER', ?, jsonb_build_object('authorizationId', ?))",
                userId, authorizationId);
        return true;
    }

    private boolean isGrantUsable(final AuthorizationRow row) {
        if (row.revokedAt() != null || !row.absoluteExpiresAt().isAfter(Instant.now())) return false;
        RegisteredClient client = clients.findByClientId(row.clientId());
        if (client == null) return false;
        InternalUser user = identities.findById(row.userId()).orElse(null);
        return user != null && user.enabled() && user.authVersion() == row.authVersion();
    }

    private OAuth2Authorization build(final AuthorizationRow row, final String presentedToken,
            final OAuth2TokenType presentedType) {
        RegisteredClient client = clients.findByClientId(row.clientId());
        if (client == null) return null;
        InternalUser user = identities.findById(row.userId()).orElse(null);
        if (user == null) return null;
        LoginUserPrincipal principal = new LoginUserPrincipal(user.id(), user.username(), user.displayName(),
                user.systemAdmin(), row.authVersion(), row.loginAt().toEpochMilli());
        List<SimpleGrantedAuthority> authorities = user.systemAdmin()
                ? List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")) : List.of();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .id(row.id()).principalName(row.userId()).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(parseScopes(row.scopes()))
                .attribute(PRINCIPAL_ATTRIBUTE, authentication)
                .attribute(OpaqueOAuth2TokenGenerator.LOGIN_AT_ATTRIBUTE, row.loginAt())
                .attribute(OpaqueOAuth2TokenGenerator.ABSOLUTE_EXPIRY_ATTRIBUTE, row.absoluteExpiresAt());
        OAuth2AuthorizationRequest request = decodeAuthorizationRequest(row.authorizationRequestJson());
        if (request != null) builder.attribute(AUTH_REQUEST_ATTRIBUTE, request);
        if (presentedType != null && AUTHORIZATION_CODE.equals(presentedType)) {
            OAuth2AuthorizationCode code = new OAuth2AuthorizationCode(presentedToken, row.codeIssuedAt(), row.codeExpiresAt());
            builder.token(code, metadata -> {
                if (row.codeUsedAt() != null) metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
            });
        }
        if (presentedType != null && OAuth2TokenType.ACCESS_TOKEN.equals(presentedType)) {
            OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, presentedToken,
                    row.accessIssuedAt(), row.accessExpiresAt(), parseScopes(row.scopes()));
            builder.accessToken(access);
        }
        if (presentedType != null && OAuth2TokenType.REFRESH_TOKEN.equals(presentedType)) {
            builder.refreshToken(new OAuth2RefreshToken(presentedToken, row.refreshIssuedAt(), row.refreshExpiresAt()));
        }
        return builder.build();
    }

    private String encodeAuthorizationRequest(final OAuth2AuthorizationRequest request) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("authorizationUri", request.getAuthorizationUri());
            data.put("clientId", request.getClientId());
            data.put("redirectUri", request.getRedirectUri());
            data.put("scopes", request.getScopes());
            data.put("state", request.getState());
            data.put("attributes", request.getAttributes());
            data.put("additionalParameters", request.getAdditionalParameters());
            return mapper.writeValueAsString(data);
        } catch (Exception failure) {
            throw new IllegalArgumentException("无法保存 OAuth 授权请求", failure);
        }
    }

    private OAuth2AuthorizationRequest decodeAuthorizationRequest(final String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return null;
        try {
            Map<String, Object> data = mapper.readValue(json, new TypeReference<>() { });
            Set<String> scopes = new LinkedHashSet<>();
            Object scopeValue = data.get("scopes");
            if (scopeValue instanceof List<?> list) list.forEach(value -> scopes.add(String.valueOf(value)));
            Map<String, Object> attributes = objectMap(data.get("attributes"));
            Map<String, Object> additional = objectMap(data.get("additionalParameters"));
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(string(data.get("authorizationUri")))
                    .clientId(string(data.get("clientId"))).redirectUri(string(data.get("redirectUri")))
                    .scopes(scopes).state(string(data.get("state"))).attributes(values -> values.putAll(attributes))
                    .additionalParameters(values -> values.putAll(additional)).build();
        } catch (Exception failure) {
            throw new IllegalStateException("持久 OAuth 授权请求格式无效", failure);
        }
    }

    private Map<String, Object> objectMap(final Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private AuthorizationRow queryByDigest(final String column, final String digest) {
        if (!Set.of("a.code_digest", "a.access_token_digest", "a.refresh_token_digest").contains(column)) {
            throw new IllegalArgumentException("不支持的 OAuth token 类型");
        }
        return jdbc.query(baseSelect() + " WHERE " + column + " = ? FOR UPDATE", ROW_MAPPER, digest)
                .stream().findFirst().orElse(null);
    }

    private String baseSelect() {
        return "SELECT a.id, a.client_record_id, c.client_id, a.user_id, a.auth_version, a.login_at, "
                + "a.absolute_expires_at, a.scopes, a.authorization_request_json::text AS authorization_request_json, "
                + "a.code_digest, a.code_issued_at, a.code_expires_at, a.code_used_at, a.access_token_digest, "
                + "a.access_issued_at, a.access_expires_at, a.access_active, a.refresh_token_digest, "
                + "a.refresh_issued_at, a.refresh_expires_at, a.revoked_at "
                + "FROM ontology.og_app_authorization a JOIN ontology.og_app_client c ON c.id = a.client_record_id";
    }

    private void insertRefreshHistory(final String authorizationId, final String tokenDigest) {
        jdbc.update("INSERT INTO ontology.og_app_refresh_token_history(token_digest, authorization_id, status) "
                        + "VALUES (?, ?, 'ACTIVE') ON CONFLICT (token_digest) DO NOTHING", tokenDigest, authorizationId);
    }

    private void revoke(final String authorizationId, final String event) {
        int changed = jdbc.update("UPDATE ontology.og_app_authorization SET revoked_at = COALESCE(revoked_at, NOW()), "
                + "access_active = FALSE, update_time = NOW() WHERE id = ?", authorizationId);
        if (changed > 0) {
            jdbc.update("UPDATE ontology.og_app_refresh_token_history SET status = 'REVOKED' "
                    + "WHERE authorization_id = ? AND status = 'ACTIVE'", authorizationId);
            jdbc.update("INSERT INTO ontology.og_audit_event(event_type, details_json) "
                    + "VALUES (?, jsonb_build_object('authorizationId', ?))", event, authorizationId);
        }
    }

    private long findAuthVersion(final String userId) {
        return identities.findById(userId).orElseThrow(() -> new IllegalArgumentException("授权用户不存在"))
                .authVersion();
    }

    private static Set<String> parseScopes(final String scopes) {
        if (scopes == null || scopes.isBlank()) return Set.of();
        return new LinkedHashSet<>(List.of(scopes.trim().split("\\s+")));
    }

    private static String digest(final String token) {
        if (token == null) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 缺少 SHA-256", impossible);
        }
    }

    private static Instant tokenIssuedAt(final OAuth2Authorization.Token<? extends org.springframework.security.oauth2.core.OAuth2Token> token) {
        return token == null ? null : token.getToken().getIssuedAt();
    }

    private static Instant tokenExpiresAt(final OAuth2Authorization.Token<? extends org.springframework.security.oauth2.core.OAuth2Token> token) {
        return token == null ? null : token.getToken().getExpiresAt();
    }

    private static Instant instant(final java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String string(final Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record RefreshHistory(String authorizationId, String status) { }
    private record AuthorizationRow(String id, String clientRecordId, String clientId, String userId,
            long authVersion, Instant loginAt, Instant absoluteExpiresAt, String scopes,
            String authorizationRequestJson, String codeDigest, Instant codeIssuedAt, Instant codeExpiresAt,
            Instant codeUsedAt, String accessTokenDigest, Instant accessIssuedAt, Instant accessExpiresAt,
            boolean accessActive, String refreshTokenDigest, Instant refreshIssuedAt, Instant refreshExpiresAt,
            Instant revokedAt) { }
}
