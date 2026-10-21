package com.devin.ontology.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 在一次性 PostgreSQL/AGE 测试库中跑通会话、PKCE、授权码和 Token 持久化。 */
@SpringBootTest(classes = com.devin.ontology.OntologyServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("multiuser")
@EnabledIfEnvironmentVariable(named = "ONTOLOGY_IT_JDBC_URL", matches = ".*ontology_multiuser_dev_20261020.*")
class AgentOAuthAuthorizationIntegrationTest {
    private static final String REDIRECT_URI = "https://app.example.test/oidc/callback";
    private static final String PUBLIC_ORIGIN = "https://ontology.example.test/";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper mapper;

    private CookieManager cookies;
    private java.net.http.HttpClient client;
    private String userId;
    private String username;
    private String viewerUserId;
    private String viewerUsername;
    private String outsiderUserId;
    private String outsiderUsername;
    private BigInteger createdProjectId;
    private String clientRecordId;
    private String secondClientRecordId;

    @DynamicPropertySource
    static void databaseProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AgentOAuthAuthorizationIntegrationTest::jdbcUrlForOntologySchema);
        registry.add("spring.datasource.username", () -> requiredEnvironment("JDBC_USER"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("JDBC_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.datasource.druid.initial-size", () -> "1");
        registry.add("spring.datasource.druid.min-idle", () -> "0");
        registry.add("spring.datasource.druid.max-active", () -> "5");
        registry.add("graph.database.type", () -> "age");
        registry.add("graph.database.age.preloaded", () -> "true");
        registry.add("ontology.workbench.enabled", () -> "false");
        // 本机 HTTP 集成测试不能发送 Secure Cookie；生产仍由正式环境配置 Secure + SameSite=None。
        registry.add("ontology.security.cookie-secure", () -> "false");
        registry.add("ontology.security.cookie-same-site", () -> "Lax");
        registry.add("ontology.security.public-origin", () -> PUBLIC_ORIGIN);
    }

    @BeforeEach
    void createAdminAndHttpClient() {
        userId = UUID.randomUUID().toString();
        username = "oauth_it_" + UUID.randomUUID().toString().replace("-", "");
        viewerUserId = UUID.randomUUID().toString();
        viewerUsername = "oauth_it_viewer_" + UUID.randomUUID().toString().replace("-", "");
        outsiderUserId = UUID.randomUUID().toString();
        outsiderUsername = "oauth_it_outsider_" + UUID.randomUUID().toString().replace("-", "");
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = java.net.http.HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        jdbc.update("INSERT INTO ontology.og_user(id, username, display_name, system_admin, enabled) "
                        + "VALUES (?, ?, 'OAuth integration admin', TRUE, TRUE)", userId, username);
        jdbc.update("INSERT INTO ontology.og_password_credential(user_id, password_hash) VALUES (?, ?)",
                userId, passwordEncoder.encode("test-only-" + userId));
        jdbc.update("INSERT INTO ontology.og_user(id, username, display_name, system_admin, enabled) "
                + "VALUES (?, ?, 'OAuth integration viewer', FALSE, TRUE)", viewerUserId, viewerUsername);
        jdbc.update("INSERT INTO ontology.og_password_credential(user_id, password_hash) VALUES (?, ?)",
                viewerUserId, passwordEncoder.encode("test-only-" + viewerUserId));
        jdbc.update("INSERT INTO ontology.og_user(id, username, display_name, system_admin, enabled) "
                + "VALUES (?, ?, 'OAuth integration outsider', FALSE, TRUE)", outsiderUserId, outsiderUsername);
        jdbc.update("INSERT INTO ontology.og_password_credential(user_id, password_hash) VALUES (?, ?)",
                outsiderUserId, passwordEncoder.encode("test-only-" + outsiderUserId));
    }

    @AfterEach
    void removeTestRows() {
        if (clientRecordId != null) {
            jdbc.update("DELETE FROM ontology.og_app_authorization WHERE client_record_id = ?", clientRecordId);
            jdbc.update("DELETE FROM ontology.og_app_authorization_consent WHERE client_record_id = ?", clientRecordId);
            jdbc.update("DELETE FROM ontology.og_app_client WHERE id = ?", clientRecordId);
        }
        if (secondClientRecordId != null) {
            jdbc.update("DELETE FROM ontology.og_app_authorization WHERE client_record_id = ?", secondClientRecordId);
            jdbc.update("DELETE FROM ontology.og_app_authorization_consent WHERE client_record_id = ?",
                    secondClientRecordId);
            jdbc.update("DELETE FROM ontology.og_app_client WHERE id = ?", secondClientRecordId);
        }
        if (createdProjectId != null) {
            jdbc.update("DELETE FROM ontology.og_project_member WHERE project_id = ?", createdProjectId);
            jdbc.update("DELETE FROM ontology.og_organization WHERE id = ?", createdProjectId);
        }
        if (userId != null) {
            jdbc.update("DELETE FROM ontology.spring_session WHERE principal_name = ?", userId);
            jdbc.update("DELETE FROM ontology.og_password_credential WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM ontology.og_user WHERE id = ?", userId);
        }
        if (viewerUserId != null) {
            jdbc.update("DELETE FROM ontology.spring_session WHERE principal_name = ?", viewerUserId);
            jdbc.update("DELETE FROM ontology.og_password_credential WHERE user_id = ?", viewerUserId);
            jdbc.update("DELETE FROM ontology.og_user WHERE id = ?", viewerUserId);
        }
        if (outsiderUserId != null) {
            jdbc.update("DELETE FROM ontology.spring_session WHERE principal_name = ?", outsiderUserId);
            jdbc.update("DELETE FROM ontology.og_password_credential WHERE user_id = ?", outsiderUserId);
            jdbc.update("DELETE FROM ontology.og_user WHERE id = ?", outsiderUserId);
        }
    }

    @Test
    void authorizationServerMetadataUsesPublicOriginAndServletContextPath() throws Exception {
        HttpResponse<String> response = get("/.well-known/oauth-authorization-server");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode metadata = mapper.readTree(response.body());
        String issuer = PUBLIC_ORIGIN.replaceAll("/+$", "") + "/api/v1";
        assertThat(metadata.path("issuer").asText()).isEqualTo(issuer);
        assertThat(metadata.path("authorization_endpoint").asText())
                .isEqualTo(issuer + "/oidc/app/authorize");
        assertThat(metadata.path("token_endpoint").asText()).isEqualTo(issuer + "/oidc/app/token");
        assertThat(metadata.path("revocation_endpoint").asText()).isEqualTo(issuer + "/oidc/app/revoke");
        // Spring Authorization Server 默认启用并声明 introspection；此断言锁定它的实际 context 路径。
        assertThat(metadata.path("introspection_endpoint").asText())
                .isEqualTo(issuer + "/oauth2/introspect");
    }

    @Test
    void loggedInUserCanApprovePkceClientAndExchangeOneTimeCode() throws Exception {
        String csrf = csrfToken();
        HttpResponse<String> login = postForm("/auth/login", csrf,
                List.of(Map.entry("username", username), Map.entry("password", "test-only-" + userId)));
        assertThat(login.statusCode()).isEqualTo(200);

        csrf = csrfToken();
        HttpResponse<String> created = postJson("/admin/oauth/clients", csrf,
                Map.of("displayName", "OAuth integration probe", "redirectUri", REDIRECT_URI,
                        "scopes", List.of("profile", "ontology.read")));
        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode clientBody = mapper.readTree(created.body());
        String clientId = clientBody.path("clientId").asText();
        String clientSecret = clientBody.path("clientSecret").asText();
        clientRecordId = jdbc.queryForObject("SELECT id FROM ontology.og_app_client WHERE client_id = ?",
                String.class, clientId);
        String storedSecret = jdbc.queryForObject(
                "SELECT client_secret_hash FROM ontology.og_app_client WHERE id = ?", String.class, clientRecordId);
        assertThat(storedSecret).isNotEqualTo(clientSecret);
        assertThat(passwordEncoder.matches(clientSecret, storedSecret)).isTrue();

        String verifier = randomVerifier();
        String challenge = challenge(verifier);
        String state = "state-" + UUID.randomUUID();
        String scope = "profile ontology.read";
        String authorizeQuery = form(List.of(Map.entry("response_type", "code"),
                Map.entry("client_id", clientId), Map.entry("redirect_uri", REDIRECT_URI),
                Map.entry("scope", scope), Map.entry("state", state),
                Map.entry("code_challenge", challenge), Map.entry("code_challenge_method", "S256")));
        HttpResponse<String> authorization = get("/oidc/app/authorize?" + authorizeQuery);
        assertThat(authorization.statusCode()).isBetween(300, 399);

        URI consentUri = resolveLocation(authorization);
        HttpResponse<String> consent = send(HttpRequest.newBuilder(consentUri).GET().build());
        assertThat(consent.statusCode()).isEqualTo(200);
        assertThat(consent.body()).contains("OAuth integration probe", "ontology.read");
        String consentState = queryParameters(consentUri.getRawQuery()).get("state");
        String storedConsentStateDigest = jdbc.queryForObject(
                "SELECT consent_state_digest FROM ontology.og_app_authorization WHERE client_record_id = ? AND user_id = ?",
                String.class, clientRecordId, userId);
        assertThat(storedConsentStateDigest).isEqualTo(sha256(consentState));

        csrf = csrfToken();
        HttpResponse<String> approved = postForm("/oidc/app/authorize", csrf,
                List.of(Map.entry("client_id", clientId), Map.entry("state", consentState),
                        Map.entry("user_oauth_approval", "true"), Map.entry("scope", "profile"),
                        Map.entry("scope", "ontology.read")));
        assertThat(approved.statusCode()).isBetween(300, 399);
        assertThat(jdbc.queryForObject(
                "SELECT consent_state_digest FROM ontology.og_app_authorization WHERE client_record_id = ? AND user_id = ?",
                String.class, clientRecordId, userId)).isNull();
        URI callback = resolveLocation(approved);
        Map<String, String> callbackParams = queryParameters(callback.getRawQuery());
        String authorizationCode = callbackParams.get("code");
        assertThat(authorizationCode).isNotBlank();
        assertThat(callbackParams.get("state")).isEqualTo(state);

        HttpResponse<String> consentReplay = postForm("/oidc/app/authorize", csrf,
                List.of(Map.entry("client_id", clientId), Map.entry("state", consentState),
                        Map.entry("user_oauth_approval", "true"), Map.entry("scope", "profile"),
                        Map.entry("scope", "ontology.read")));
        assertThat(consentReplay.statusCode()).isEqualTo(400);

        HttpResponse<String> tokenResponse = postForm("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "authorization_code"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("redirect_uri", REDIRECT_URI),
                        Map.entry("code", authorizationCode), Map.entry("code_verifier", verifier)));
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        JsonNode tokenBody = mapper.readTree(tokenResponse.body());
        String accessToken = tokenBody.path("access_token").asText();
        String refreshToken = tokenBody.path("refresh_token").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        assertThat(tokenBody.path("expires_in").asLong()).isBetween(1L, 900L);

        String[] digests = jdbc.queryForObject("SELECT code_digest, access_token_digest, refresh_token_digest "
                        + "FROM ontology.og_app_authorization WHERE client_record_id = ? AND user_id = ?",
                (rs, rowNum) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)},
                clientRecordId, userId);
        assertThat(digests[0]).isEqualTo(sha256(authorizationCode));
        assertThat(digests[1]).isEqualTo(sha256(accessToken));
        assertThat(digests[2]).isEqualTo(sha256(refreshToken));
        assertThat(digests).doesNotContain(authorizationCode, accessToken, refreshToken);

        Instant[] grantTimes = jdbc.queryForObject("SELECT login_at, absolute_expires_at "
                        + "FROM ontology.og_app_authorization WHERE client_record_id = ? AND user_id = ?",
                (rs, rowNum) -> new Instant[] {rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()},
                clientRecordId, userId);
        assertThat(Duration.between(grantTimes[0], grantTimes[1])).isEqualTo(Duration.ofHours(8));

        csrf = csrfToken();
        HttpResponse<String> secondCreated = postJson("/admin/oauth/clients", csrf,
                Map.of("displayName", "OAuth second-client probe", "redirectUri", REDIRECT_URI,
                        "scopes", List.of("profile", "ontology.read")));
        assertThat(secondCreated.statusCode()).isEqualTo(200);
        JsonNode secondClientBody = mapper.readTree(secondCreated.body());
        String secondClientId = secondClientBody.path("clientId").asText();
        String secondClientSecret = secondClientBody.path("clientSecret").asText();
        secondClientRecordId = jdbc.queryForObject("SELECT id FROM ontology.og_app_client WHERE client_id = ?",
                String.class, secondClientId);
        HttpResponse<String> crossClientRefresh = postForm("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "refresh_token"), Map.entry("client_id", secondClientId),
                        Map.entry("client_secret", secondClientSecret), Map.entry("refresh_token", refreshToken)));
        assertThat(crossClientRefresh.statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT status FROM ontology.og_app_refresh_token_history "
                        + "WHERE token_digest = ?", String.class, sha256(refreshToken)))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM ontology.og_app_authorization "
                        + "WHERE client_record_id = ? AND user_id = ?", Boolean.class, clientRecordId, userId))
                .isTrue();

        HttpResponse<String> refreshed = postForm("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "refresh_token"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("refresh_token", refreshToken)));
        assertThat(refreshed.statusCode()).isEqualTo(200);
        JsonNode refreshedBody = mapper.readTree(refreshed.body());
        String refreshedAccessToken = refreshedBody.path("access_token").asText();
        String rotatedRefreshToken = refreshedBody.path("refresh_token").asText();
        assertThat(refreshedAccessToken).isNotBlank().isNotEqualTo(accessToken);
        assertThat(rotatedRefreshToken).isNotBlank().isNotEqualTo(refreshToken);
        assertThat(refreshedBody.path("expires_in").asLong()).isBetween(1L, 900L);
        String[] rotatedState = jdbc.queryForObject("SELECT access_token_digest, refresh_token_digest, "
                        + "access_expires_at, refresh_expires_at, absolute_expires_at "
                        + "FROM ontology.og_app_authorization WHERE client_record_id = ? AND user_id = ?",
                (rs, rowNum) -> new String[] {rs.getString(1), rs.getString(2),
                        rs.getTimestamp(3).toInstant().toString(), rs.getTimestamp(4).toInstant().toString(),
                        rs.getTimestamp(5).toInstant().toString()}, clientRecordId, userId);
        assertThat(rotatedState[0]).isEqualTo(sha256(refreshedAccessToken));
        assertThat(rotatedState[1]).isEqualTo(sha256(rotatedRefreshToken));
        assertThat(Instant.parse(rotatedState[2])).isBeforeOrEqualTo(grantTimes[1]);
        assertThat(Instant.parse(rotatedState[3])).isBeforeOrEqualTo(grantTimes[1]);
        assertThat(jdbc.queryForObject("SELECT status FROM ontology.og_app_refresh_token_history "
                        + "WHERE token_digest = ?", String.class, sha256(refreshToken)))
                .isEqualTo("REPLACED");

        HttpRequest concurrentOne = formRequest("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "refresh_token"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("refresh_token", rotatedRefreshToken)));
        HttpRequest concurrentTwo = formRequest("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "refresh_token"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("refresh_token", rotatedRefreshToken)));
        CompletableFuture<HttpResponse<String>> concurrentResultOne = client.sendAsync(concurrentOne,
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> concurrentResultTwo = client.sendAsync(concurrentTwo,
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> raceOne = concurrentResultOne.get(20, TimeUnit.SECONDS);
        HttpResponse<String> raceTwo = concurrentResultTwo.get(20, TimeUnit.SECONDS);
        List<HttpResponse<String>> raceResults = List.of(raceOne, raceTwo);
        assertThat(raceResults.stream().filter(result -> result.statusCode() == 200).count()).isEqualTo(1L);
        assertThat(raceResults.stream().filter(result -> result.statusCode() == 400).count()).isEqualTo(1L);
        HttpResponse<String> raceWinner = raceResults.stream().filter(result -> result.statusCode() == 200)
                .findFirst().orElseThrow();
        String concurrentAccessToken = mapper.readTree(raceWinner.body()).path("access_token").asText();
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL AND access_active = FALSE "
                        + "FROM ontology.og_app_authorization WHERE client_record_id = ? AND user_id = ?",
                Boolean.class, clientRecordId, userId)).isTrue();
        HttpResponse<String> concurrentRevokedAccess = send(HttpRequest.newBuilder(uri("/oidc/app/userinfo"))
                .header("Authorization", "Bearer " + concurrentAccessToken).GET().build());
        assertThat(concurrentRevokedAccess.statusCode()).isEqualTo(401);

        HttpResponse<String> reusedRefresh = postForm("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "refresh_token"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("refresh_token", refreshToken)));
        assertThat(reusedRefresh.statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL AND access_active = FALSE "
                        + "FROM ontology.og_app_authorization WHERE client_record_id = ? AND user_id = ?",
                Boolean.class, clientRecordId, userId)).isTrue();
        HttpResponse<String> revokedAccess = send(HttpRequest.newBuilder(uri("/oidc/app/userinfo"))
                .header("Authorization", "Bearer " + refreshedAccessToken).GET().build());
        assertThat(revokedAccess.statusCode()).isEqualTo(401);

        HttpResponse<String> replay = postForm("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "authorization_code"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("redirect_uri", REDIRECT_URI),
                        Map.entry("code", authorizationCode), Map.entry("code_verifier", verifier)));
        assertThat(replay.statusCode()).isEqualTo(400);

        String failedRequestId = replay.headers().firstValue("X-Request-ID").orElseThrow();
        String auditDetails = jdbc.queryForObject("SELECT details_json::text FROM ontology.og_audit_event "
                        + "WHERE event_type = 'SECURITY_OAUTH_PROTOCOL_REJECTED' "
                        + "AND details_json->>'requestId' = ?", String.class, failedRequestId);
        assertThat(auditDetails).contains(failedRequestId, "/oidc/app/token", "400")
                .doesNotContain(clientSecret, authorizationCode, verifier, accessToken);

        HttpResponse<String> auditPage = get("/admin/audit-events?limit=20");
        assertThat(auditPage.statusCode()).isEqualTo(200);
        assertThat(auditPage.body()).contains("SECURITY_OAUTH_PROTOCOL_REJECTED", failedRequestId);
    }

    @Test
    void onlySystemAdministratorsCanReadAuditEvents() throws Exception {
        CookieManager viewerCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        java.net.http.HttpClient viewerClient = java.net.http.HttpClient.newBuilder().cookieHandler(viewerCookies)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        HttpResponse<String> csrfResponse = get(viewerClient, "/auth/csrf");
        assertThat(csrfResponse.statusCode()).isEqualTo(200);
        String csrf = mapper.readTree(csrfResponse.body()).path("token").asText();
        HttpResponse<String> login = postForm(viewerClient, "/auth/login", csrf,
                List.of(Map.entry("username", viewerUsername),
                        Map.entry("password", "test-only-" + viewerUserId)));
        assertThat(login.statusCode()).isEqualTo(200);

        HttpResponse<String> denied = get(viewerClient, "/admin/audit-events?limit=20");

        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_audit_event "
                        + "WHERE event_type = 'SECURITY_ACCESS_DENIED' AND actor_user_id = ? "
                        + "AND details_json->>'path' = '/api/v1/admin/audit-events'", Long.class, viewerUserId))
                .isEqualTo(1L);
    }

    @Test
    void anonymousAuthorizeRedirectsToLoginAndRfc7009RevokesTokensWithAndWithoutHint() throws Exception {
        String csrf = csrfToken();
        HttpResponse<String> loggedIn = postForm("/auth/login", csrf,
                List.of(Map.entry("username", username), Map.entry("password", "test-only-" + userId)));
        assertThat(loggedIn.statusCode()).isEqualTo(200);
        csrf = csrfToken();
        HttpResponse<String> created = postJson("/admin/oauth/clients", csrf,
                Map.of("displayName", "RFC 7009 revoke probe", "redirectUri", REDIRECT_URI,
                        "scopes", List.of("profile")));
        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode clientBody = mapper.readTree(created.body());
        String clientId = clientBody.path("clientId").asText();
        String clientSecret = clientBody.path("clientSecret").asText();
        clientRecordId = jdbc.queryForObject("SELECT id FROM ontology.og_app_client WHERE client_id = ?",
                String.class, clientId);

        String state = "anonymous-state-" + UUID.randomUUID();
        String verifier = randomVerifier();
        String query = form(List.of(Map.entry("response_type", "code"), Map.entry("client_id", clientId),
                Map.entry("redirect_uri", REDIRECT_URI), Map.entry("scope", "profile"),
                Map.entry("state", state), Map.entry("code_challenge", challenge(verifier)),
                Map.entry("code_challenge_method", "S256")));
        java.net.http.HttpClient anonymousClient = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        HttpResponse<String> anonymousAuthorize = get(anonymousClient, "/oidc/app/authorize?" + query);
        assertThat(anonymousAuthorize.statusCode()).isBetween(300, 399);
        URI login = resolveLocation(anonymousAuthorize);
        assertThat(login.getPath()).isEqualTo("/ontology-management/login");
        assertThat(queryParameters(login.getRawQuery()).get("oauth_return"))
                .isEqualTo("/api/v1/oidc/app/authorize?" + query);
        HttpResponse<String> anonymousPost = send(anonymousClient, formRequest("/oidc/app/authorize", null,
                List.of(Map.entry("client_id", clientId), Map.entry("state", state))));
        assertThat(anonymousPost.statusCode()).isBetween(400, 499);

        JsonNode accessGrant = issueTokenPair(clientId, clientSecret);
        String accessToken = accessGrant.path("access_token").asText();
        String accessAuthorizationId = authorizationIdForToken("access_token_digest", accessToken);
        HttpResponse<String> revokeAccess = postForm("/oidc/app/revoke", null,
                List.of(Map.entry("client_id", clientId), Map.entry("client_secret", clientSecret),
                        Map.entry("token", accessToken)));
        assertThat(revokeAccess.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT access_active FROM ontology.og_app_authorization WHERE id = ?",
                Boolean.class, accessAuthorizationId)).isFalse();
        assertThat(getBearer(accessToken, "/oidc/app/userinfo", null).statusCode()).isEqualTo(401);
        assertThat(rejectedRefresh(clientId, clientSecret, accessGrant.path("refresh_token").asText()))
                .isEqualTo(400);

        JsonNode expiredAccessGrant = issueTokenPair(clientId, clientSecret);
        String expiredAccessToken = expiredAccessGrant.path("access_token").asText();
        String expiredAuthorizationId = authorizationIdForToken("access_token_digest", expiredAccessToken);
        jdbc.update("UPDATE ontology.og_app_authorization SET access_issued_at = NOW() - INTERVAL '16 minutes', "
                + "access_expires_at = NOW() - INTERVAL '1 second' "
                + "WHERE id = ?", expiredAuthorizationId);
        HttpResponse<String> expiredAccessRevoke = postForm("/oidc/app/revoke", null,
                List.of(Map.entry("client_id", clientId), Map.entry("client_secret", clientSecret),
                        Map.entry("token", expiredAccessToken)));
        assertThat(expiredAccessRevoke.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL AND access_active = FALSE "
                + "FROM ontology.og_app_authorization WHERE id = ?", Boolean.class, expiredAuthorizationId)).isTrue();
        assertThat(rejectedRefresh(clientId, clientSecret, expiredAccessGrant.path("refresh_token").asText()))
                .isEqualTo(400);

        JsonNode refreshGrant = issueTokenPair(clientId, clientSecret);
        String refreshAccessToken = refreshGrant.path("access_token").asText();
        String refreshToken = refreshGrant.path("refresh_token").asText();
        String refreshAuthorizationId = authorizationIdForToken("refresh_token_digest", refreshToken);
        assertThat(jdbc.queryForObject("SELECT access_token_digest FROM ontology.og_app_authorization WHERE id = ?",
                String.class, refreshAuthorizationId)).isEqualTo(sha256(refreshAccessToken));
        HttpResponse<String> revokeRefresh = postForm("/oidc/app/revoke", null,
                List.of(Map.entry("client_id", clientId), Map.entry("client_secret", clientSecret),
                        Map.entry("token", refreshToken), Map.entry("token_type_hint", "refresh_token")));
        assertThat(revokeRefresh.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT access_token_digest FROM ontology.og_app_authorization WHERE id = ?",
                String.class, refreshAuthorizationId)).isEqualTo(sha256(refreshAccessToken));
        assertThat(jdbc.queryForObject("SELECT status FROM ontology.og_app_refresh_token_history "
                        + "WHERE token_digest = ?", String.class, sha256(refreshToken)))
                .isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM ontology.og_app_authorization "
                + "WHERE id = ?", Boolean.class, refreshAuthorizationId)).isTrue();
        assertThat(getBearer(refreshGrant.path("access_token").asText(), "/oidc/app/userinfo", null)
                .statusCode()).isEqualTo(401);
        assertThat(rejectedRefresh(clientId, clientSecret, refreshToken)).isEqualTo(400);

        JsonNode mismatchedHintGrant = issueTokenPair(clientId, clientSecret);
        String mismatchedAccessToken = mismatchedHintGrant.path("access_token").asText();
        String mismatchedAuthorizationId = authorizationIdForToken("access_token_digest", mismatchedAccessToken);
        HttpResponse<String> mismatchedHint = postForm("/oidc/app/revoke", null,
                List.of(Map.entry("client_id", clientId), Map.entry("client_secret", clientSecret),
                        Map.entry("token", mismatchedAccessToken), Map.entry("token_type_hint", "refresh_token")));
        assertThat(mismatchedHint.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM ontology.og_app_authorization "
                + "WHERE id = ?", Boolean.class, mismatchedAuthorizationId)).isTrue();
        assertThat(rejectedRefresh(clientId, clientSecret, mismatchedHintGrant.path("refresh_token").asText()))
                .isEqualTo(400);

        JsonNode racingGrant = issueTokenPair(clientId, clientSecret);
        String racingAccessToken = racingGrant.path("access_token").asText();
        String racingRefreshToken = racingGrant.path("refresh_token").asText();
        String racingAuthorizationId = authorizationIdForToken("refresh_token_digest", racingRefreshToken);
        HttpRequest racingRefreshRequest = formRequest("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "refresh_token"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("refresh_token", racingRefreshToken)));
        HttpRequest racingRevokeRequest = formRequest("/oidc/app/revoke", null,
                List.of(Map.entry("client_id", clientId), Map.entry("client_secret", clientSecret),
                        Map.entry("token", racingRefreshToken)));
        CompletableFuture<HttpResponse<String>> racingRefresh = client.sendAsync(racingRefreshRequest,
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> racingRevoke = client.sendAsync(racingRevokeRequest,
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> racingRefreshResponse = racingRefresh.get(20, TimeUnit.SECONDS);
        HttpResponse<String> racingRevokeResponse = racingRevoke.get(20, TimeUnit.SECONDS);
        assertThat(racingRevokeResponse.statusCode()).isEqualTo(200);
        assertThat(racingRefreshResponse.statusCode()).isIn(200, 400);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL AND access_active = FALSE "
                + "FROM ontology.og_app_authorization WHERE id = ?", Boolean.class, racingAuthorizationId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_app_refresh_token_history "
                + "WHERE authorization_id = ? AND status = 'ACTIVE'", Long.class, racingAuthorizationId)).isZero();
        assertThat(getBearer(racingAccessToken, "/oidc/app/userinfo", null).statusCode()).isEqualTo(401);
        if (racingRefreshResponse.statusCode() == 200) {
            JsonNode raced = mapper.readTree(racingRefreshResponse.body());
            assertThat(getBearer(raced.path("access_token").asText(), "/oidc/app/userinfo", null).statusCode())
                    .isEqualTo(401);
            assertThat(rejectedRefresh(clientId, clientSecret, raced.path("refresh_token").asText())).isEqualTo(400);
        }

        jdbc.update("UPDATE ontology.og_user SET auth_version = auth_version + 1 WHERE id = ?", userId);
        HttpResponse<String> staleSessionAuthorize = get("/oidc/app/authorize?" + query);
        assertThat(staleSessionAuthorize.statusCode()).isBetween(300, 399);
        assertThat(resolveLocation(staleSessionAuthorize).getPath())
                .isEqualTo("/ontology-management/login");
    }

    private int rejectedRefresh(final String clientId, final String clientSecret, final String refreshToken)
            throws Exception {
        return postForm("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "refresh_token"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("refresh_token", refreshToken)))
                .statusCode();
    }

    private JsonNode issueTokenPair(final String clientId, final String clientSecret) throws Exception {
        String verifier = randomVerifier();
        String state = "revoke-state-" + UUID.randomUUID();
        String query = form(List.of(Map.entry("response_type", "code"), Map.entry("client_id", clientId),
                Map.entry("redirect_uri", REDIRECT_URI), Map.entry("scope", "profile"),
                Map.entry("state", state), Map.entry("code_challenge", challenge(verifier)),
                Map.entry("code_challenge_method", "S256")));
        HttpResponse<String> authorize = get("/oidc/app/authorize?" + query);
        URI next = resolveLocation(authorize);
        String code;
        if (isLocalTestEndpoint(next)) {
            // 首次授权会进入本机 consent；已有 consent 时 SAS 会直接跳回 callback。
            HttpResponse<String> consent = send(HttpRequest.newBuilder(next).GET().build());
            assertThat(consent.statusCode()).isEqualTo(200);
            String consentState = queryParameters(next.getRawQuery()).get("state");
            String csrf = csrfToken();
            HttpResponse<String> approved = postForm("/oidc/app/authorize", csrf,
                    List.of(Map.entry("client_id", clientId), Map.entry("state", consentState),
                            Map.entry("user_oauth_approval", "true"), Map.entry("scope", "profile")));
            code = callbackCode(resolveLocation(approved), state);
        } else {
            // 自动同意的回调只从 Location 读取 code，测试禁止访问 callback 域名。
            code = callbackCode(next, state);
        }
        HttpResponse<String> response = postForm("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "authorization_code"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("redirect_uri", REDIRECT_URI),
                        Map.entry("code", code), Map.entry("code_verifier", verifier)));
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private String authorizationIdForToken(final String column, final String token) throws Exception {
        if (!Set.of("access_token_digest", "refresh_token_digest").contains(column)) {
            throw new IllegalArgumentException("unsupported token column");
        }
        return jdbc.queryForObject("SELECT id FROM ontology.og_app_authorization WHERE " + column + " = ?",
                String.class, sha256(token));
    }

    @Test
    void usersCanOnlyRevokeTheirOwnAppAuthorizationAndRevocationInvalidatesTokens() throws Exception {
        String csrf = csrfToken();
        HttpResponse<String> login = postForm("/auth/login", csrf,
                List.of(Map.entry("username", username), Map.entry("password", "test-only-" + userId)));
        assertThat(login.statusCode()).isEqualTo(200);

        csrf = csrfToken();
        HttpResponse<String> created = postJson("/admin/oauth/clients", csrf,
                Map.of("displayName", "Authorization revoke probe", "redirectUri", REDIRECT_URI,
                        "scopes", List.of("profile")));
        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode clientBody = mapper.readTree(created.body());
        String clientId = clientBody.path("clientId").asText();
        String clientSecret = clientBody.path("clientSecret").asText();
        clientRecordId = jdbc.queryForObject("SELECT id FROM ontology.og_app_client WHERE client_id = ?",
                String.class, clientId);

        String verifier = randomVerifier();
        String state = "revoke-state-" + UUID.randomUUID();
        String authorizeQuery = form(List.of(Map.entry("response_type", "code"),
                Map.entry("client_id", clientId), Map.entry("redirect_uri", REDIRECT_URI),
                Map.entry("scope", "profile"), Map.entry("state", state),
                Map.entry("code_challenge", challenge(verifier)), Map.entry("code_challenge_method", "S256")));
        HttpResponse<String> authorization = get("/oidc/app/authorize?" + authorizeQuery);
        URI consentUri = resolveLocation(authorization);
        HttpResponse<String> consent = send(HttpRequest.newBuilder(consentUri).GET().build());
        assertThat(consent.statusCode()).isEqualTo(200);
        String consentState = queryParameters(consentUri.getRawQuery()).get("state");

        csrf = csrfToken();
        HttpResponse<String> approved = postForm("/oidc/app/authorize", csrf,
                List.of(Map.entry("client_id", clientId), Map.entry("state", consentState),
                        Map.entry("user_oauth_approval", "true"), Map.entry("scope", "profile")));
        assertThat(approved.statusCode()).isBetween(300, 399);
        URI callback = resolveLocation(approved);
        String authorizationCode = queryParameters(callback.getRawQuery()).get("code");
        assertThat(authorizationCode).isNotBlank();
        HttpResponse<String> tokenResponse = postForm("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "authorization_code"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("redirect_uri", REDIRECT_URI),
                        Map.entry("code", authorizationCode), Map.entry("code_verifier", verifier)));
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        JsonNode tokenBody = mapper.readTree(tokenResponse.body());
        String accessToken = tokenBody.path("access_token").asText();
        String refreshToken = tokenBody.path("refresh_token").asText();
        String authorizationId = jdbc.queryForObject("SELECT id FROM ontology.og_app_authorization "
                + "WHERE client_record_id = ? AND user_id = ?", String.class, clientRecordId, userId);

        HttpResponse<String> ownList = get("/auth/app-authorizations");
        assertThat(ownList.statusCode()).isEqualTo(200);
        assertThat(ownList.body()).contains("Authorization revoke probe")
                .doesNotContain(accessToken, refreshToken, clientSecret);
        assertThat(send(HttpRequest.newBuilder(uri("/oidc/app/userinfo"))
                .header("Authorization", "Bearer " + accessToken).GET().build()).statusCode()).isEqualTo(200);

        // 第二个用户只能看到自己的列表，尝试撤销他人授权也不能影响原授权。
        CookieManager viewerCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        java.net.http.HttpClient viewerClient = java.net.http.HttpClient.newBuilder().cookieHandler(viewerCookies)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        String viewerCsrf = csrfToken(viewerClient);
        HttpResponse<String> viewerLogin = postForm(viewerClient, "/auth/login", viewerCsrf,
                List.of(Map.entry("username", viewerUsername),
                        Map.entry("password", "test-only-" + viewerUserId)));
        assertThat(viewerLogin.statusCode()).isEqualTo(200);
        viewerCsrf = csrfToken(viewerClient);
        HttpResponse<String> viewerList = get(viewerClient, "/auth/app-authorizations");
        assertThat(viewerList.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(viewerList.body())).isEmpty();
        HttpResponse<String> crossUserRevoke = send(viewerClient, HttpRequest.newBuilder(
                uri("/auth/app-authorizations/" + authorizationId)).header("X-CSRF-TOKEN", viewerCsrf)
                .DELETE().build());
        assertThat(crossUserRevoke.statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM ontology.og_app_authorization "
                + "WHERE id = ?", Boolean.class, authorizationId)).isTrue();

        csrf = csrfToken();
        HttpRequest revokeRequest = HttpRequest.newBuilder(uri("/auth/app-authorizations/" + authorizationId))
                .header("X-CSRF-TOKEN", csrf).DELETE().build();
        HttpRequest refreshRequest = formRequest("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "refresh_token"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("refresh_token", refreshToken)));
        CompletableFuture<HttpResponse<String>> revokeFuture = client.sendAsync(revokeRequest,
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> refreshFuture = client.sendAsync(refreshRequest,
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> revoked = revokeFuture.get(20, TimeUnit.SECONDS);
        HttpResponse<String> concurrentRefresh = refreshFuture.get(20, TimeUnit.SECONDS);
        assertThat(revoked.statusCode()).isEqualTo(200);
        assertThat(concurrentRefresh.statusCode()).isIn(200, 400);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL AND access_active = FALSE "
                + "FROM ontology.og_app_authorization WHERE id = ?", Boolean.class, authorizationId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_app_refresh_token_history "
                + "WHERE authorization_id = ? AND status = 'ACTIVE'", Long.class, authorizationId)).isZero();
        assertThat(send(HttpRequest.newBuilder(uri("/oidc/app/userinfo"))
                .header("Authorization", "Bearer " + accessToken).GET().build()).statusCode()).isEqualTo(401);
        if (concurrentRefresh.statusCode() == 200) {
            JsonNode rotated = mapper.readTree(concurrentRefresh.body());
            String racedAccessToken = rotated.path("access_token").asText();
            String racedRefreshToken = rotated.path("refresh_token").asText();
            assertThat(racedAccessToken).isNotBlank();
            assertThat(racedRefreshToken).isNotBlank();
            assertThat(send(HttpRequest.newBuilder(uri("/oidc/app/userinfo"))
                    .header("Authorization", "Bearer " + racedAccessToken).GET().build()).statusCode()).isEqualTo(401);
            HttpResponse<String> retryRacedRefresh = postForm("/oidc/app/token", null,
                    List.of(Map.entry("grant_type", "refresh_token"), Map.entry("client_id", clientId),
                            Map.entry("client_secret", clientSecret), Map.entry("refresh_token", racedRefreshToken)));
            assertThat(retryRacedRefresh.statusCode()).isEqualTo(400);
        }
    }

    @Test
    void agentScopesIntersectProjectMembershipAndIgnoreSpoofedIdentityHeaders() throws Exception {
        String csrf = csrfToken();
        HttpResponse<String> adminLogin = postForm("/auth/login", csrf,
                List.of(Map.entry("username", username), Map.entry("password", "test-only-" + userId)));
        assertThat(adminLogin.statusCode()).isEqualTo(200);

        csrf = csrfToken();
        HttpResponse<String> managerClientResponse = postJson("/admin/oauth/clients", csrf,
                Map.of("displayName", "Project authorization probe", "redirectUri", REDIRECT_URI,
                        "scopes", List.of("profile", "ontology.read", "ontology.manage")));
        assertThat(managerClientResponse.statusCode()).isEqualTo(200);
        JsonNode managerClientBody = mapper.readTree(managerClientResponse.body());
        String managerClientId = managerClientBody.path("clientId").asText();
        String managerClientSecret = managerClientBody.path("clientSecret").asText();
        clientRecordId = jdbc.queryForObject("SELECT id FROM ontology.og_app_client WHERE client_id = ?",
                String.class, managerClientId);

        csrf = csrfToken();
        HttpResponse<String> readerClientResponse = postJson("/admin/oauth/clients", csrf,
                Map.of("displayName", "Project read-only probe", "redirectUri", REDIRECT_URI,
                        "scopes", List.of("profile", "ontology.read")));
        assertThat(readerClientResponse.statusCode()).isEqualTo(200);
        JsonNode readerClientBody = mapper.readTree(readerClientResponse.body());
        String readerClientId = readerClientBody.path("clientId").asText();
        String readerClientSecret = readerClientBody.path("clientSecret").asText();
        secondClientRecordId = jdbc.queryForObject("SELECT id FROM ontology.og_app_client WHERE client_id = ?",
                String.class, readerClientId);

        CookieManager viewerCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        java.net.http.HttpClient viewerClient = java.net.http.HttpClient.newBuilder().cookieHandler(viewerCookies)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        String viewerCsrf = csrfToken(viewerClient);
        HttpResponse<String> viewerLogin = postForm(viewerClient, "/auth/login", viewerCsrf,
                List.of(Map.entry("username", viewerUsername),
                        Map.entry("password", "test-only-" + viewerUserId)));
        assertThat(viewerLogin.statusCode()).isEqualTo(200);
        String ownerToken = authorizeAndExchange(viewerClient, managerClientId, managerClientSecret,
                "profile ontology.read ontology.manage");

        HttpResponse<String> projectResponse = postBearerJson(ownerToken, "/organization/save",
                Map.of("name", "oauth-project-" + UUID.randomUUID(), "description", "OAuth ACL test"));
        assertThat(projectResponse.statusCode()).isEqualTo(200);
        JsonNode created = mapper.readTree(projectResponse.body());
        assertThat(created.path("success").asBoolean()).isTrue();
        createdProjectId = new BigInteger(created.path("data").path("id").asText());
        assertThat(jdbc.queryForObject("SELECT role FROM ontology.og_project_member "
                + "WHERE project_id = ? AND user_id = ?", String.class, createdProjectId, viewerUserId))
                .isEqualTo("OWNER");

        HttpResponse<String> ownerMembers = getBearer(ownerToken,
                "/projects/" + createdProjectId + "/members?pageNum=1&pageSize=20", null);
        assertThat(ownerMembers.statusCode()).isEqualTo(200);
        assertThat(ownerMembers.body()).contains(viewerUserId, "OWNER");

        CookieManager outsiderCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        java.net.http.HttpClient outsiderClient = java.net.http.HttpClient.newBuilder().cookieHandler(outsiderCookies)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        String outsiderCsrf = csrfToken(outsiderClient);
        HttpResponse<String> outsiderLogin = postForm(outsiderClient, "/auth/login", outsiderCsrf,
                List.of(Map.entry("username", outsiderUsername),
                        Map.entry("password", "test-only-" + outsiderUserId)));
        assertThat(outsiderLogin.statusCode()).isEqualTo(200);
        String outsiderReadToken = authorizeAndExchange(outsiderClient, readerClientId, readerClientSecret,
                "profile ontology.read");
        String outsiderManagerToken = authorizeAndExchange(outsiderClient, managerClientId, managerClientSecret,
                "profile ontology.manage");

        HttpResponse<String> ownerPage = getBearer(ownerToken, "/organization/page", null);
        assertThat(ownerPage.statusCode()).isEqualTo(200);
        assertThat(ownerPage.body()).contains(createdProjectId.toString());
        HttpResponse<String> outsiderPage = getBearer(outsiderReadToken, "/organization/page", null);
        assertThat(outsiderPage.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(outsiderPage.body()).path("data")).isEmpty();

        HttpResponse<String> readOnlyWrite = postBearerJson(outsiderReadToken, "/organization/save",
                Map.of("name", "must-not-be-created", "description", "scope rejection probe"));
        assertThat(readOnlyWrite.statusCode()).isEqualTo(403);

        HttpResponse<String> spoofedIdentity = getBearer(outsiderManagerToken, "/oidc/app/userinfo", viewerUserId);
        assertThat(mapper.readTree(spoofedIdentity.body()).path("sub").asText()).isEqualTo(outsiderUserId);
        HttpResponse<String> outsiderProjectAccess = getBearer(outsiderManagerToken,
                "/projects/" + createdProjectId + "/members?pageNum=1&pageSize=20", viewerUserId);
        assertThat(outsiderProjectAccess.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_organization "
                + "WHERE name = 'must-not-be-created'", Long.class)).isZero();
    }

    @Test
    void ordinaryWebLogoutKeepsAgentGrantButLogoutAllRevokesIt() throws Exception {
        String csrf = csrfToken();
        HttpResponse<String> adminLogin = postForm("/auth/login", csrf,
                List.of(Map.entry("username", username), Map.entry("password", "test-only-" + userId)));
        assertThat(adminLogin.statusCode()).isEqualTo(200);
        csrf = csrfToken();
        HttpResponse<String> clientResponse = postJson("/admin/oauth/clients", csrf,
                Map.of("displayName", "Logout boundary probe", "redirectUri", REDIRECT_URI,
                        "scopes", List.of("profile")));
        assertThat(clientResponse.statusCode()).isEqualTo(200);
        JsonNode clientBody = mapper.readTree(clientResponse.body());
        String appClientId = clientBody.path("clientId").asText();
        String appClientSecret = clientBody.path("clientSecret").asText();
        clientRecordId = jdbc.queryForObject("SELECT id FROM ontology.og_app_client WHERE client_id = ?",
                String.class, appClientId);

        CookieManager viewerCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        java.net.http.HttpClient viewerClient = java.net.http.HttpClient.newBuilder().cookieHandler(viewerCookies)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        String viewerCsrf = csrfToken(viewerClient);
        HttpResponse<String> viewerLogin = postForm(viewerClient, "/auth/login", viewerCsrf,
                List.of(Map.entry("username", viewerUsername),
                        Map.entry("password", "test-only-" + viewerUserId)));
        assertThat(viewerLogin.statusCode()).isEqualTo(200);
        String accessToken = authorizeAndExchange(viewerClient, appClientId, appClientSecret, "profile");

        viewerCsrf = csrfToken(viewerClient);
        HttpResponse<String> ordinaryLogout = postForm(viewerClient, "/auth/logout", viewerCsrf, List.of());
        assertThat(ordinaryLogout.statusCode()).isEqualTo(200);
        assertThat(getBearer(accessToken, "/oidc/app/userinfo", null).statusCode()).isEqualTo(200);

        viewerCsrf = csrfToken(viewerClient);
        HttpResponse<String> relogin = postForm(viewerClient, "/auth/login", viewerCsrf,
                List.of(Map.entry("username", viewerUsername),
                        Map.entry("password", "test-only-" + viewerUserId)));
        assertThat(relogin.statusCode()).isEqualTo(200);
        viewerCsrf = csrfToken(viewerClient);
        HttpResponse<String> logoutAll = send(viewerClient, HttpRequest.newBuilder(uri("/auth/logout-all"))
                .header("X-CSRF-TOKEN", viewerCsrf).POST(HttpRequest.BodyPublishers.noBody()).build());
        assertThat(logoutAll.statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL AND access_active = FALSE "
                + "FROM ontology.og_app_authorization WHERE client_record_id = ? AND user_id = ?",
                Boolean.class, clientRecordId, viewerUserId)).isTrue();
        assertThat(getBearer(accessToken, "/oidc/app/userinfo", null).statusCode()).isEqualTo(401);
    }

    private String csrfToken() throws Exception {
        HttpResponse<String> response = get("/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body()).path("token").asText();
    }

    private String csrfToken(final java.net.http.HttpClient sessionClient) throws Exception {
        HttpResponse<String> response = get(sessionClient, "/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body()).path("token").asText();
    }

    private HttpResponse<String> get(final String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    private HttpResponse<String> get(final java.net.http.HttpClient sessionClient, final String path)
            throws IOException, InterruptedException {
        return send(sessionClient, HttpRequest.newBuilder(uri(path)).GET().build());
    }

    private String authorizeAndExchange(final java.net.http.HttpClient sessionClient,
            final String clientId, final String clientSecret, final String scopes) throws Exception {
        String verifier = randomVerifier();
        String state = "agent-state-" + UUID.randomUUID();
        String query = form(List.of(Map.entry("response_type", "code"), Map.entry("client_id", clientId),
                Map.entry("redirect_uri", REDIRECT_URI), Map.entry("scope", scopes), Map.entry("state", state),
                Map.entry("code_challenge", challenge(verifier)), Map.entry("code_challenge_method", "S256")));
        HttpResponse<String> authorization = get(sessionClient, "/oidc/app/authorize?" + query);
        assertThat(authorization.statusCode()).isBetween(300, 399);
        URI consentUri = resolveLocation(authorization);
        HttpResponse<String> consent = send(sessionClient, HttpRequest.newBuilder(consentUri).GET().build());
        assertThat(consent.statusCode()).isEqualTo(200);
        String consentState = queryParameters(consentUri.getRawQuery()).get("state");
        String csrf = csrfToken(sessionClient);
        List<Map.Entry<String, String>> approval = new java.util.ArrayList<>(List.of(
                Map.entry("client_id", clientId), Map.entry("state", consentState),
                Map.entry("user_oauth_approval", "true")));
        for (String scope : scopes.split("\\s+")) approval.add(Map.entry("scope", scope));
        HttpResponse<String> approved = postForm(sessionClient, "/oidc/app/authorize", csrf, approval);
        assertThat(approved.statusCode()).isBetween(300, 399);
        String code = queryParameters(resolveLocation(approved).getRawQuery()).get("code");
        assertThat(code).isNotBlank();
        HttpResponse<String> tokens = postForm("/oidc/app/token", null,
                List.of(Map.entry("grant_type", "authorization_code"), Map.entry("client_id", clientId),
                        Map.entry("client_secret", clientSecret), Map.entry("redirect_uri", REDIRECT_URI),
                        Map.entry("code", code), Map.entry("code_verifier", verifier)));
        assertThat(tokens.statusCode()).isEqualTo(200);
        return mapper.readTree(tokens.body()).path("access_token").asText();
    }

    private HttpResponse<String> getBearer(final String accessToken, final String path, final String spoofedUserId)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + accessToken).GET();
        if (spoofedUserId != null) request.header("X-User-ID", spoofedUserId);
        return send(request.build());
    }

    private HttpResponse<String> postBearerJson(final String accessToken, final String path, final Object body)
            throws IOException, InterruptedException {
        return postBearerJson(accessToken, path, body, null);
    }

    private HttpResponse<String> postBearerJson(final String accessToken, final String path,
            final Object body, final String spoofedUserId) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (spoofedUserId != null) request.header("X-User-ID", spoofedUserId);
        return send(request.build());
    }

    private HttpResponse<String> postForm(final String path, final String csrf,
            final List<Map.Entry<String, String>> fields) throws IOException, InterruptedException {
        return postForm(client, path, csrf, fields);
    }

    private HttpResponse<String> postForm(final java.net.http.HttpClient sessionClient, final String path,
            final String csrf, final List<Map.Entry<String, String>> fields)
            throws IOException, InterruptedException {
        return send(sessionClient, formRequest(path, csrf, fields));
    }

    private HttpRequest formRequest(final String path, final String csrf,
            final List<Map.Entry<String, String>> fields) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded");
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        return request.POST(HttpRequest.BodyPublishers.ofString(form(fields))).build();
    }

    private HttpResponse<String> postJson(final String path, final String csrf, final Object body)
            throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build());
    }

    private HttpResponse<String> send(final HttpRequest request) throws IOException, InterruptedException {
        return send(client, request);
    }

    private HttpResponse<String> send(final java.net.http.HttpClient sessionClient, final HttpRequest request)
            throws IOException, InterruptedException {
        if (!isLocalTestEndpoint(request.uri())) {
            throw new AssertionError("OAuth integration test blocked a non-local HTTP request: " + request.uri());
        }
        return sessionClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean isLocalTestEndpoint(final URI value) {
        return "http".equalsIgnoreCase(value.getScheme()) && "127.0.0.1".equals(value.getHost())
                && value.getPort() == port && value.getPath().startsWith("/api/v1/");
    }

    private String callbackCode(final URI callback, final String expectedState) {
        URI expected = URI.create(REDIRECT_URI);
        assertThat(callback.getScheme()).isEqualTo(expected.getScheme());
        assertThat(callback.getHost()).isEqualTo(expected.getHost());
        assertThat(callback.getPort()).isEqualTo(expected.getPort());
        assertThat(callback.getPath()).isEqualTo(expected.getPath());
        Map<String, String> parameters = queryParameters(callback.getRawQuery());
        assertThat(parameters.get("state")).isEqualTo(expectedState);
        assertThat(parameters.get("code")).isNotBlank();
        return parameters.get("code");
    }

    private URI uri(final String path) {
        return URI.create("http://127.0.0.1:" + port + "/api/v1" + path);
    }

    private URI resolveLocation(final HttpResponse<String> response) {
        String location = response.headers().firstValue("location").orElseThrow();
        URI value = URI.create(location);
        return value.isAbsolute() ? value : uri("/").resolve(value);
    }

    private String form(final List<Map.Entry<String, String>> fields) {
        return fields.stream().map(field -> encode(field.getKey()) + "=" + encode(field.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, String> queryParameters(final String query) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 1 ? "" : java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    private String randomVerifier() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String challenge(final String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String sha256(final String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static String requiredEnvironment(final String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required isolated database environment variable is missing");
        }
        return value;
    }

    private static String jdbcUrlForOntologySchema() {
        String url = requiredEnvironment("ONTOLOGY_IT_JDBC_URL");
        // 实际业务实体使用未限定表名，测试连接必须显式使用应用 schema。
        if (!url.matches("(?i)^jdbc:postgresql://[^/]+/ontology_multiuser_dev_20261020(?:\\?.*)?$")) {
            throw new IllegalStateException("OAuth integration test is restricted to the named development copy");
        }
        String withoutCurrentSchema = url.replaceAll("(?i)([?&])currentSchema=[^&]*", "")
                .replaceAll("[?&]$", "");
        return withoutCurrentSchema + (withoutCurrentSchema.contains("?") ? "&" : "?")
                + "currentSchema=ontology";
    }
}
