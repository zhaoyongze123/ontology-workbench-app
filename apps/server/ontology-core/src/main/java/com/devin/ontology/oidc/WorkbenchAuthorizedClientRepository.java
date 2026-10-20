package com.devin.ontology.oidc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/** 只使用 OIDC ID Token 完成登录，不把上游 access/refresh token 持久化到 session。 */
final class WorkbenchAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            final String clientRegistrationId, final Authentication principal, final HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(final OAuth2AuthorizedClient authorizedClient,
            final Authentication principal, final HttpServletRequest request, final HttpServletResponse response) {
        // 有意不持久化上游令牌。
    }

    @Override
    public void removeAuthorizedClient(final String clientRegistrationId, final Authentication principal,
            final HttpServletRequest request, final HttpServletResponse response) {
        // 此仓储永远不保存客户端令牌。
    }
}
