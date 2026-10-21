package com.devin.ontology.oauth;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.CurrentIdentity;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只允许系统管理员维护 Workbench App 客户端；新 Secret 仅在创建结果中出现一次。 */
@Service
public class AppClientManagementService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentClientRepository clients;
    private final CurrentIdentity currentIdentity;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public AppClientManagementService(final AgentClientRepository clients,
            final CurrentIdentity currentIdentity, final PasswordEncoder passwordEncoder,
            final JdbcTemplate jdbc) {
        this.clients = clients;
        this.currentIdentity = currentIdentity;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    public java.util.List<AgentClientRepository.AppClientView> list() {
        requireAdmin();
        return clients.list();
    }

    @Transactional
    public AgentClientRepository.CreatedAppClient create(final CreateClientRequest request) {
        InternalUser actor = requireAdmin();
        if (request == null || request.displayName() == null || request.displayName().isBlank()
                || request.displayName().length() > 160) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        String redirectUri = validateRedirectUri(request.redirectUri());
        Set<String> scopes = request.scopes() == null ? Set.of() : new HashSet<>(request.scopes());
        if (scopes.stream().anyMatch(Objects::isNull) || !scopes.contains("profile") || scopes.isEmpty()
                || !AgentClientRepository.supportedScopes().containsAll(scopes)) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }

        String id = UUID.randomUUID().toString();
        String clientId = "ontology_" + randomValue(18);
        String clientSecret = randomValue(32);
        var client = AgentClientRepository.newClient(id, clientId,
                passwordEncoder.encode(clientSecret), request.displayName().trim(), redirectUri, scopes);
        clients.save(client);
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES ('APP_CLIENT_CREATED', ?, jsonb_build_object('clientId', ?, 'scopes', ?::jsonb))",
                actor.id(), clientId, toJsonArray(scopes));
        return new AgentClientRepository.CreatedAppClient(clientId, clientSecret,
                request.displayName().trim(), redirectUri, scopes);
    }

    @Transactional
    public void disable(final String clientRecordId) {
        InternalUser actor = requireAdmin();
        String clientId = clients.clientIdForRecord(clientRecordId);
        if (clientId == null) throw new OntologyApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        jdbc.update("UPDATE ontology.og_app_client SET enabled = FALSE, update_time = NOW() WHERE id = ?",
                clientRecordId);
        jdbc.update("UPDATE ontology.og_app_authorization SET revoked_at = COALESCE(revoked_at, NOW()), "
                        + "access_active = FALSE, update_time = NOW() WHERE client_record_id = ?",
                clientRecordId);
        jdbc.update("UPDATE ontology.og_app_refresh_token_history h SET status = 'REVOKED' "
                        + "FROM ontology.og_app_authorization a "
                        + "WHERE h.authorization_id = a.id AND a.client_record_id = ? AND h.status <> 'REVOKED'",
                clientRecordId);
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES ('APP_CLIENT_DISABLED', ?, jsonb_build_object('clientId', ?))",
                actor.id(), clientId);
    }

    private InternalUser requireAdmin() {
        InternalUser user = currentIdentity.requireUser();
        if (!user.systemAdmin()) throw new AccessDeniedException("系统管理员权限不足");
        return user;
    }

    private String validateRedirectUri(final String value) {
        if (value == null || value.isBlank() || value.length() > 2048
                || value.contains("*") || value.chars().anyMatch(Character::isWhitespace)) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getFragment() != null) {
                throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException invalid) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID, invalid);
        }
    }

    private String randomValue(final int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String toJsonArray(final Set<String> scopes) {
        return scopes.stream().sorted().map(scope -> "\"" + scope + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    public record CreateClientRequest(String displayName, String redirectUri, Set<String> scopes) { }
}
