package com.devin.ontology.oauth;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.CurrentIdentity;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
        ClientSettings settings = validateSettings(request == null ? null : request.displayName(),
                request == null ? null : request.redirectUri(), request == null ? null : request.scopes());

        String id = UUID.randomUUID().toString();
        String clientId = "ontology_" + randomValue(18);
        String clientSecret = randomValue(32);
        var client = AgentClientRepository.newClient(id, clientId,
                passwordEncoder.encode(clientSecret), settings.displayName(), settings.redirectUri(), settings.scopes());
        clients.save(client);
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES ('APP_CLIENT_CREATED', ?, jsonb_build_object('clientId', ?, 'scopes', ?::jsonb))",
                actor.id(), clientId, toJsonArray(settings.scopes()));
        return new AgentClientRepository.CreatedAppClient(clientId, clientSecret,
                settings.displayName(), settings.redirectUri(), settings.scopes());
    }

    @Transactional
    public AgentClientRepository.AppClientView update(final String clientRecordId,
            final UpdateClientRequest request) {
        InternalUser actor = requireAdmin();
        AgentClientRepository.AppClientView current = clients.lockForUpdate(clientRecordId);
        if (current == null) throw new OntologyApiException(ApiErrorCode.RESOURCE_NOT_FOUND);

        ClientSettings settings = validateSettings(request == null ? null : request.displayName(),
                request == null ? null : request.redirectUri(), request == null ? null : request.scopes());
        boolean nameChanged = !current.displayName().equals(settings.displayName());
        boolean redirectChanged = !current.redirectUri().equals(settings.redirectUri());
        boolean scopesChanged = !current.scopes().equals(settings.scopes());
        if (!nameChanged && !redirectChanged && !scopesChanged) return current;

        clients.updateConfiguration(clientRecordId, settings.displayName(), settings.redirectUri(), settings.scopes());
        if (redirectChanged || scopesChanged) {
            // 回调和权限范围改变后，旧授权不再代表当前配置；撤销令牌并要求用户重新授权。
            revokeClientAuthorizations(clientRecordId);
            clearClientConsents(clientRecordId);
        }

        Set<String> changedFields = new LinkedHashSet<>();
        if (nameChanged) changedFields.add("displayName");
        if (redirectChanged) changedFields.add("redirectUri");
        if (scopesChanged) changedFields.add("scopes");
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES ('APP_CLIENT_UPDATED', ?, jsonb_build_object('clientId', ?, 'changedFields', ?::jsonb))",
                actor.id(), current.clientId(), toJsonArray(changedFields));
        return clients.viewByRecordId(clientRecordId);
    }

    @Transactional
    public void disable(final String clientRecordId) {
        InternalUser actor = requireAdmin();
        AgentClientRepository.AppClientView current = clients.lockForUpdate(clientRecordId);
        if (current == null) throw new OntologyApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        if (current.enabled()) {
            clients.setEnabled(clientRecordId, false);
            jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                            + "VALUES ('APP_CLIENT_DISABLED', ?, jsonb_build_object('clientId', ?))",
                    actor.id(), current.clientId());
        }
        revokeClientAuthorizations(clientRecordId);
        clearClientConsents(clientRecordId);
    }

    @Transactional
    public void enable(final String clientRecordId) {
        InternalUser actor = requireAdmin();
        AgentClientRepository.AppClientView current = clients.lockForUpdate(clientRecordId);
        if (current == null) throw new OntologyApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        if (current.enabled()) return;

        clients.setEnabled(clientRecordId, true);
        // 启用只允许发起新授权，不会清除旧授权的 revoked_at，也不会恢复旧令牌。
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES ('APP_CLIENT_ENABLED', ?, jsonb_build_object('clientId', ?))",
                actor.id(), current.clientId());
    }

    @Transactional
    public RotatedClientSecret rotateSecret(final String clientRecordId) {
        InternalUser actor = requireAdmin();
        AgentClientRepository.AppClientView current = clients.lockForUpdate(clientRecordId);
        if (current == null) throw new OntologyApiException(ApiErrorCode.RESOURCE_NOT_FOUND);

        String clientSecret = randomValue(32);
        clients.updateSecretHash(clientRecordId, passwordEncoder.encode(clientSecret));
        revokeClientAuthorizations(clientRecordId);
        clearClientConsents(clientRecordId);
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES ('APP_CLIENT_SECRET_ROTATED', ?, jsonb_build_object('clientId', ?))",
                actor.id(), current.clientId());
        return new RotatedClientSecret(current.clientId(), clientSecret);
    }

    private void revokeClientAuthorizations(final String clientRecordId) {
        jdbc.update("UPDATE ontology.og_app_authorization SET revoked_at = COALESCE(revoked_at, NOW()), "
                        + "consent_state_digest = NULL, consent_state_expires_at = NULL, "
                        + "access_active = FALSE, update_time = NOW() WHERE client_record_id = ?",
                clientRecordId);
        jdbc.update("UPDATE ontology.og_app_refresh_token_history h SET status = 'REVOKED' "
                        + "FROM ontology.og_app_authorization a "
                        + "WHERE h.authorization_id = a.id AND a.client_record_id = ? AND h.status <> 'REVOKED'",
                clientRecordId);
    }

    private void clearClientConsents(final String clientRecordId) {
        jdbc.update("DELETE FROM ontology.og_app_authorization_consent WHERE client_record_id = ?", clientRecordId);
    }

    private ClientSettings validateSettings(final String displayName, final String redirectUri,
            final Set<String> requestedScopes) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 160) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        String validatedRedirectUri = validateRedirectUri(redirectUri);
        Set<String> scopes = requestedScopes == null ? Set.of() : new HashSet<>(requestedScopes);
        if (scopes.stream().anyMatch(Objects::isNull) || !scopes.contains("profile") || scopes.isEmpty()
                || !AgentClientRepository.supportedScopes().containsAll(scopes)) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        return new ClientSettings(displayName.trim(), validatedRedirectUri, Set.copyOf(scopes));
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

    public record UpdateClientRequest(String displayName, String redirectUri, Set<String> scopes) { }

    public record RotatedClientSecret(String clientId, String clientSecret) { }

    private record ClientSettings(String displayName, String redirectUri, Set<String> scopes) { }
}
