package com.devin.ontology.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Repository;

/** Workbench 客户端配置存储；Secret 只存 PasswordEncoder 产生的单向编码。 */
@Repository
public class AgentClientRepository implements RegisteredClientRepository {
    private static final Set<String> SUPPORTED_SCOPES = Set.of(
            "profile", "ontology.read", "ontology.write", "ontology.manage");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AgentClientRepository(final JdbcTemplate jdbc, final ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void save(final RegisteredClient client) {
        if (client.getRedirectUris().size() != 1
                || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)
                || !client.getClientSettings().isRequireProofKey()
                || !SUPPORTED_SCOPES.containsAll(client.getScopes())) {
            throw new IllegalArgumentException("Ontology App 客户端协议配置不符合固定安全策略");
        }
        String redirectUri = client.getRedirectUris().iterator().next();
        String scopes = String.join(" ", client.getScopes().stream().sorted().toList());
        jdbc.update("INSERT INTO ontology.og_app_client(id, client_id, client_secret_hash, display_name, "
                        + "redirect_uri, scopes, enabled, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, TRUE, NOW(), NOW()) "
                        + "ON CONFLICT (id) DO UPDATE SET client_id = EXCLUDED.client_id, "
                        + "client_secret_hash = EXCLUDED.client_secret_hash, "
                        + "display_name = EXCLUDED.display_name, redirect_uri = EXCLUDED.redirect_uri, "
                        + "scopes = EXCLUDED.scopes, enabled = TRUE, update_time = NOW()",
                client.getId(), client.getClientId(), client.getClientSecret(), client.getClientName(),
                redirectUri, scopes);
    }

    @Override
    public RegisteredClient findById(final String id) {
        return query("WHERE id = ?", id);
    }

    @Override
    public RegisteredClient findByClientId(final String clientId) {
        return query("WHERE client_id = ? AND enabled = TRUE", clientId);
    }

    public List<AppClientView> list() {
        return jdbc.query("SELECT id, client_id, display_name, redirect_uri, scopes, enabled, create_time "
                        + "FROM ontology.og_app_client ORDER BY create_time DESC",
                (rs, rowNum) -> new AppClientView(rs.getString("id"), rs.getString("client_id"),
                        rs.getString("display_name"), rs.getString("redirect_uri"),
                        parseScopes(rs.getString("scopes")), rs.getBoolean("enabled"),
                        rs.getTimestamp("create_time").toInstant()));
    }

    public AppClientView viewByClientId(final String clientId) {
        return jdbc.query("SELECT id, client_id, display_name, redirect_uri, scopes, enabled, create_time "
                        + "FROM ontology.og_app_client WHERE client_id = ?",
                (rs, rowNum) -> new AppClientView(rs.getString("id"), rs.getString("client_id"),
                        rs.getString("display_name"), rs.getString("redirect_uri"),
                        parseScopes(rs.getString("scopes")), rs.getBoolean("enabled"),
                        rs.getTimestamp("create_time").toInstant()), clientId)
                .stream().findFirst().orElse(null);
    }

    public String clientIdForRecord(final String recordId) {
        return jdbc.query("SELECT client_id FROM ontology.og_app_client WHERE id = ?",
                (rs, rowNum) -> rs.getString(1), recordId).stream().findFirst().orElse(null);
    }

    public static Set<String> supportedScopes() {
        return SUPPORTED_SCOPES;
    }

    public static RegisteredClient newClient(final String id, final String clientId,
            final String encodedSecret, final String name, final String redirectUri,
            final Set<String> scopes) {
        TokenSettings tokenSettings = TokenSettings.builder()
                .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                .accessTokenTimeToLive(Duration.ofMinutes(15))
                .refreshTokenTimeToLive(Duration.ofHours(8))
                .authorizationCodeTimeToLive(Duration.ofMinutes(5))
                .reuseRefreshTokens(false)
                .build();
        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(clientId)
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientName(name)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .clientSettings(ClientSettings.builder().requireProofKey(true)
                        .requireAuthorizationConsent(true).build())
                .tokenSettings(tokenSettings);
        scopes.stream().sorted().forEach(builder::scope);
        return builder.build();
    }

    private RegisteredClient query(final String condition, final String value) {
        return jdbc.query("SELECT id, client_id, client_secret_hash, display_name, redirect_uri, scopes "
                        + "FROM ontology.og_app_client " + condition,
                (rs, rowNum) -> {
                    Set<String> scopes = parseScopes(rs.getString("scopes"));
                    return newClient(rs.getString("id"), rs.getString("client_id"),
                            rs.getString("client_secret_hash"), rs.getString("display_name"),
                            rs.getString("redirect_uri"), scopes);
                }, value).stream().findFirst().orElse(null);
    }

    private Set<String> parseScopes(final String scopes) {
        if (scopes == null || scopes.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.asList(scopes.trim().split("\\s+")));
    }

    public record AppClientView(String id, String clientId, String displayName, String redirectUri,
            Set<String> scopes, boolean enabled, Instant createdAt) { }

    public record CreatedAppClient(String clientId, String clientSecret, String displayName,
            String redirectUri, Set<String> scopes) { }
}
