package com.devin.ontology.oauth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;

/** 在授权请求进入 Spring Authorization Server 前记录服务端读取的客户端安全配置版本。 */
final class ClientConfigurationSnapshotAuthenticationConverter implements AuthenticationConverter {
    static final String CONFIG_FINGERPRINT_PARAMETER = "__ontology_client_config_fingerprint";

    private final OAuth2AuthorizationCodeRequestAuthenticationConverter delegate =
            new OAuth2AuthorizationCodeRequestAuthenticationConverter();
    private final AgentClientRepository clients;

    ClientConfigurationSnapshotAuthenticationConverter(final AgentClientRepository clients) {
        this.clients = clients;
    }

    @Override
    public Authentication convert(final jakarta.servlet.http.HttpServletRequest request) {
        Authentication converted = delegate.convert(request);
        if (!(converted instanceof OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest)) {
            return converted;
        }

        String fingerprint = clients.authorizationConfigFingerprintByClientId(authorizationRequest.getClientId());
        if (fingerprint == null) return converted;

        Map<String, Object> additionalParameters = new LinkedHashMap<>();
        authorizationRequest.getAdditionalParameters().forEach((key, value) ->
                additionalParameters.put(String.valueOf(key), value));
        // 由服务端覆盖保留字段，客户端不能伪造快照；该字段仅在授权请求持久化时使用。
        additionalParameters.put(CONFIG_FINGERPRINT_PARAMETER, fingerprint);
        Authentication principal = authorizationRequest.getPrincipal() instanceof Authentication value
                ? value : null;
        OAuth2AuthorizationCodeRequestAuthenticationToken enriched =
                new OAuth2AuthorizationCodeRequestAuthenticationToken(authorizationRequest.getAuthorizationUri(),
                        authorizationRequest.getClientId(), principal, authorizationRequest.getRedirectUri(),
                        authorizationRequest.getState(), authorizationRequest.getScopes(), additionalParameters);
        enriched.setDetails(authorizationRequest.getDetails());
        return enriched;
    }
}
