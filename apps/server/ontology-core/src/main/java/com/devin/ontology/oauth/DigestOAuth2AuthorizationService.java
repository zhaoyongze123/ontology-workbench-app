package com.devin.ontology.oauth;

import com.devin.ontology.identity.IdentityRepository;
import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.LoginUserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.Duration;
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
    private static final OAuth2TokenType CONSENT_STATE = new OAuth2TokenType(OAuth2ParameterNames.STATE);
    private static final Duration CONSENT_STATE_LIFETIME = Duration.ofMinutes(5);
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
            rs.getString("consent_state_digest"), instant(rs.getTimestamp("consent_state_expires_at")),
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
        String consentState = authorization.getAttribute(OAuth2ParameterNames.STATE);
        Instant consentStateExpiresAt = consentState == null ? null : Instant.now().plus(CONSENT_STATE_LIFETIME);
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
                            + "login_at, absolute_expires_at, scopes, authorization_request_json, "
                            + "consent_state_digest, consent_state_expires_at, code_digest, "
                            + "code_issued_at, code_expires_at, code_used_at, access_token_digest, access_issued_at, "
                            + "access_expires_at, access_active, refresh_token_digest, refresh_issued_at, "
                            + "refresh_expires_at, create_time, update_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    authorization.getId(), authorization.getRegisteredClientId(), userId, authVersion,
                    sqlTimestamp(finalLoginAt), sqlTimestamp(finalAbsoluteExpiry), scopes, requestJson,
                    digest(consentState), sqlTimestamp(consentStateExpiresAt), digest(codeValue),
                    sqlTimestamp(tokenIssuedAt(code)), sqlTimestamp(tokenExpiresAt(code)),
                    sqlTimestamp(codeUsedAt), digest(accessValue), sqlTimestamp(tokenIssuedAt(access)),
                    sqlTimestamp(tokenExpiresAt(access)), accessActive, digest(refreshValue),
                    sqlTimestamp(tokenIssuedAt(refresh)), sqlTimestamp(tokenExpiresAt(refresh)));
            if (refreshValue != null && refresh.isActive()) insertRefreshHistory(authorization.getId(), digest(refreshValue));
            return;
        }

        // queryForObject 可正确返回 SQL NULL；Stream.findFirst() 遇到 null 会抛 NPE。
        String oldRefreshDigest = jdbc.queryForObject("SELECT refresh_token_digest "
                + "FROM ontology.og_app_authorization WHERE id = ? FOR UPDATE", String.class, authorization.getId());
        String newRefreshDigest = digest(refreshValue);
        // 本应用按授权族撤销：RFC 7009 收到 access/refresh token revoke 后，不能留下仍可刷新的半授权。
        // 只识别这次协议对象里的访问/刷新令牌失效，授权码消费本身不属于撤销。
        boolean accessRevoked = access != null && access.isInvalidated() && accessValue != null;
        boolean refreshRevoked = refresh != null && refresh.isInvalidated() && refreshValue != null;
        if (accessRevoked || refreshRevoked) {
            revoke(authorization.getId(), "APP_TOKEN_REVOKED");
            return;
        }
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
                        + "consent_state_digest = CASE WHEN ? THEN NULL ELSE COALESCE(?, consent_state_digest) END, "
                        + "consent_state_expires_at = CASE WHEN ? THEN NULL "
                        + "ELSE COALESCE(?, consent_state_expires_at) END, "
                        + "access_token_digest = COALESCE(?, access_token_digest), "
                        + "access_issued_at = COALESCE(?, access_issued_at), "
                        + "access_expires_at = COALESCE(?, access_expires_at), "
                        + "access_active = CASE WHEN ? THEN TRUE WHEN ? THEN FALSE ELSE access_active END, "
                        + "refresh_token_digest = COALESCE(?, refresh_token_digest), "
                        + "refresh_issued_at = COALESCE(?, refresh_issued_at), "
                        + "refresh_expires_at = COALESCE(?, refresh_expires_at), update_time = NOW() WHERE id = ?",
                scopes, requestJson, requestJson, digest(codeValue), sqlTimestamp(tokenIssuedAt(code)),
                sqlTimestamp(tokenExpiresAt(code)), sqlTimestamp(codeUsedAt), codeValue != null, digest(consentState),
                codeValue != null, sqlTimestamp(consentStateExpiresAt), digest(accessValue),
                sqlTimestamp(tokenIssuedAt(access)), sqlTimestamp(tokenExpiresAt(access)), accessActive,
                access != null, newRefreshDigest, sqlTimestamp(tokenIssuedAt(refresh)),
                sqlTimestamp(tokenExpiresAt(refresh)), authorization.getId());
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
                .stream().findFirst().map(row -> build(row, null, null, false)).orElse(null);
    }

    @Override
    @Transactional
    public OAuth2Authorization findByToken(final String token, OAuth2TokenType tokenType) {
        if (token == null || token.isBlank()) return null;
        // 本地 SAS 1.5.8 撤销 Provider 固定以 null 类型查找；这种查找只供 RFC 7009
        // revoke 使用，允许过期 access token 被定位以撤销整个授权族。Bearer 认证始终传明确类型并检查过期。
        boolean revocationLookup = tokenType == null;
        String tokenDigest = digest(token);
        AuthorizationRow row;
        if (tokenType == null) {
            // RFC 7009 客户端通常省略 token_type_hint；仅按摘要定位，不走刷新校验，避免把撤销查询
            // 误判成 refresh reuse 并触发额外撤销副作用。
            row = queryByDigest("a.access_token_digest", tokenDigest);
            if (row != null) tokenType = OAuth2TokenType.ACCESS_TOKEN;
            else {
                row = queryByDigest("a.refresh_token_digest", tokenDigest);
                if (row != null) tokenType = OAuth2TokenType.REFRESH_TOKEN;
                else {
                    // 如果 refresh 与 revoke 并发且 refresh 先轮换，旧 digest 已不在授权主行；
                    // 通过历史表仅定位该授权族，供 RFC 7009 撤销，不触发 refresh reuse 逻辑。
                    var priorRefresh = jdbc.query("SELECT authorization_id FROM ontology.og_app_refresh_token_history "
                                    + "WHERE token_digest = ? AND status = 'REPLACED'",
                            (rs, rowNum) -> rs.getString(1), tokenDigest).stream().findFirst().orElse(null);
                    if (priorRefresh != null && lockAuthorization(priorRefresh)) {
                        row = jdbc.query(baseSelect() + " WHERE a.id = ? FOR UPDATE OF a",
                                ROW_MAPPER, priorRefresh).stream().findFirst().orElse(null);
                        if (row != null) tokenType = OAuth2TokenType.REFRESH_TOKEN;
                    }
                }
            }
            if (row == null) return null;
        } else if (AUTHORIZATION_CODE.equals(tokenType)) {
            row = queryByDigest("a.code_digest", tokenDigest);
        } else if (CONSENT_STATE.equals(tokenType)) {
            row = queryByDigest("a.consent_state_digest", tokenDigest);
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            row = queryByDigest("a.access_token_digest", tokenDigest);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            // 先锁授权主记录，再锁刷新历史；用户撤销也按这个顺序写入，避免刷新/撤销互锁。
            var history = jdbc.query("SELECT authorization_id, status FROM ontology.og_app_refresh_token_history "
                            + "WHERE token_digest = ?",
                    (rs, rowNum) -> new RefreshHistory(rs.getString("authorization_id"), rs.getString("status")),
                    tokenDigest).stream().findFirst().orElse(null);
            if (history == null) return null;
            if (!lockAuthorization(history.authorizationId())) return null;
            var currentHistory = jdbc.query("SELECT authorization_id, status FROM ontology.og_app_refresh_token_history "
                            + "WHERE token_digest = ? FOR UPDATE",
                    (rs, rowNum) -> new RefreshHistory(rs.getString("authorization_id"), rs.getString("status")),
                    tokenDigest).stream().findFirst().orElse(null);
            if (currentHistory == null || !history.authorizationId().equals(currentHistory.authorizationId())) {
                return null;
            }
            history = currentHistory;
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
        if (CONSENT_STATE.equals(tokenType)) {
            if (row.consentStateExpiresAt() == null || !row.consentStateExpiresAt().isAfter(Instant.now())) {
                jdbc.update("UPDATE ontology.og_app_authorization SET consent_state_digest = NULL, "
                        + "consent_state_expires_at = NULL, update_time = NOW() WHERE id = ?", row.id());
                return null;
            }
            int consumed = jdbc.update("UPDATE ontology.og_app_authorization SET consent_state_digest = NULL, "
                            + "consent_state_expires_at = NULL, update_time = NOW() "
                            + "WHERE id = ? AND consent_state_digest = ? AND consent_state_expires_at > NOW()",
                    row.id(), tokenDigest);
            if (consumed != 1) return null;
        }
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)
                && (!row.accessActive() || (!revocationLookup
                        && (row.accessExpiresAt() == null || !row.accessExpiresAt().isAfter(Instant.now()))))) {
            return null;
        }
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)
                && (row.refreshExpiresAt() == null || !row.refreshExpiresAt().isAfter(Instant.now()))) {
            revoke(row.id(), "APP_REFRESH_TOKEN_EXPIRED");
            return null;
        }
        return build(row, token, tokenType, revocationLookup);
    }

    @Transactional
    public boolean revokeForUser(final String authorizationId, final String userId) {
        int owned = jdbc.update("UPDATE ontology.og_app_authorization SET revoked_at = COALESCE(revoked_at, NOW()), "
                + "consent_state_digest = NULL, consent_state_expires_at = NULL, "
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
            final OAuth2TokenType presentedType, final boolean revocationLookup) {
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
        if (CONSENT_STATE.equals(presentedType)) {
            builder.attribute(OAuth2ParameterNames.STATE, presentedToken);
        }
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
            if (revocationLookup) {
                // SAS 的 Builder.invalidate(refresh) 会同步失效 access token，并假定授权对象中存在它。
                // 持久层只保存摘要，撤销查询无法还原原 access 值；此占位对象仅用于撤销上下文，
                // save() 识别失效标记后会先撤销整组授权并立即返回，不会把占位值写入数据库。
                String revokeOnlyAccessValue = "revocation-only:" + row.id();
                builder.accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        revokeOnlyAccessValue, row.accessIssuedAt(), row.accessExpiresAt(), parseScopes(row.scopes())));
            }
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
        if (!Set.of("a.code_digest", "a.consent_state_digest", "a.access_token_digest",
                "a.refresh_token_digest").contains(column)) {
            throw new IllegalArgumentException("不支持的 OAuth token 类型");
        }
        return jdbc.query(baseSelect() + " WHERE " + column + " = ? FOR UPDATE OF a", ROW_MAPPER, digest)
                .stream().findFirst().orElse(null);
    }

    private boolean lockAuthorization(final String authorizationId) {
        return !jdbc.query("SELECT id FROM ontology.og_app_authorization WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getString(1), authorizationId).isEmpty();
    }

    private String baseSelect() {
        return "SELECT a.id, a.client_record_id, c.client_id, a.user_id, a.auth_version, a.login_at, "
                + "a.absolute_expires_at, a.scopes, a.authorization_request_json::text AS authorization_request_json, "
                + "a.consent_state_digest, a.consent_state_expires_at, a.code_digest, a.code_issued_at, "
                + "a.code_expires_at, a.code_used_at, a.access_token_digest, "
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
                + "consent_state_digest = NULL, consent_state_expires_at = NULL, "
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

    /** 将 OAuth 协议时间显式转为 JDBC 时间戳，避免 PostgreSQL 驱动无法推断 Instant 的 SQL 类型。 */
    private static java.sql.Timestamp sqlTimestamp(final Instant value) {
        return value == null ? null : java.sql.Timestamp.from(value);
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
            String consentStateDigest, Instant consentStateExpiresAt, Instant revokedAt) { }
}
