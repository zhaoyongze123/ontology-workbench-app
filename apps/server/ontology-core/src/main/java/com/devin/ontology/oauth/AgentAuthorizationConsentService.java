package com.devin.ontology.oauth;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 使用 Ontology 内部 user_id 和 App 客户端 ID 作为授权同意的事实键。 */
@Repository
public class AgentAuthorizationConsentService implements OAuth2AuthorizationConsentService {
    private final JdbcTemplate jdbc;

    public AgentAuthorizationConsentService(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(final OAuth2AuthorizationConsent consent) {
        Set<String> scopes = consent.getScopes();
        jdbc.update("INSERT INTO ontology.og_app_authorization_consent "
                        + "(client_record_id, user_id, scopes, update_time) VALUES (?, ?, ?, NOW()) "
                        + "ON CONFLICT (client_record_id, user_id) DO UPDATE "
                        + "SET scopes = EXCLUDED.scopes, update_time = NOW()",
                consent.getRegisteredClientId(), consent.getPrincipalName(),
                scopes.stream().sorted().collect(Collectors.joining(" ")));
    }

    @Override
    @Transactional
    public void remove(final OAuth2AuthorizationConsent consent) {
        jdbc.update("DELETE FROM ontology.og_app_authorization_consent "
                        + "WHERE client_record_id = ? AND user_id = ?",
                consent.getRegisteredClientId(), consent.getPrincipalName());
    }

    @Override
    public OAuth2AuthorizationConsent findById(final String registeredClientId, final String principalName) {
        return jdbc.query("SELECT scopes FROM ontology.og_app_authorization_consent "
                        + "WHERE client_record_id = ? AND user_id = ?",
                (rs, rowNum) -> {
                    OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent
                            .withId(registeredClientId, principalName);
                    String encoded = rs.getString("scopes");
                    if (encoded != null && !encoded.isBlank()) {
                        for (String scope : encoded.trim().split("\\s+")) builder.scope(scope);
                    }
                    return builder.build();
                }, registeredClientId, principalName).stream().findFirst().orElse(null);
    }
}
