package com.devin.ontology.oidc;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Workbench OIDC 的服务端配置；敏感 client secret 仅从配置源读取。 */
@ConfigurationProperties(prefix = "ontology.workbench")
public class WorkbenchOidcProperties {
    private boolean enabled;
    private String issuer;
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String jwksUri;
    private String clientId;
    private String clientSecret;
    private String clientAuthMethod = "post";
    private String redirectUri;
    private List<String> scopes = new ArrayList<>(List.of("openid", "profile", "email"));

    public void validate() {
        requireHttps(issuer, "issuer", false);
        requireHttps(authorizationEndpoint, "authorizationEndpoint", true);
        requireHttps(tokenEndpoint, "tokenEndpoint", true);
        requireHttps(jwksUri, "jwksUri", true);
        URI callback = requireHttps(redirectUri, "redirectUri", true);
        if (!"/oidc/callback".equals(callback.getPath()) || !hasText(clientId) || !hasText(clientSecret)) {
            throw new IllegalStateException("Workbench OIDC 配置缺少必需字段");
        }
        if (!List.of("post", "basic").contains(clientAuthMethod)
                || scopes == null || scopes.isEmpty() || !scopes.contains("openid")
                || scopes.stream().anyMatch(scope -> !hasText(scope) || scope.chars().anyMatch(Character::isWhitespace))) {
            throw new IllegalStateException("Workbench OIDC 客户端配置无效");
        }
    }

    private static URI requireHttps(final String value, final String field, final boolean allowPath) {
        try {
            URI uri = URI.create(value == null ? "" : value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getQuery() != null || (!allowPath && !uri.getPath().isEmpty())) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException exception) {
            // 不在异常中回显可能带有凭据或查询参数的完整配置值。
            throw new IllegalStateException("Workbench OIDC " + field + " 必须是可信 HTTPS 地址");
        }
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(final boolean enabled) { this.enabled = enabled; }
    public String getIssuer() { return issuer; }
    public void setIssuer(final String issuer) { this.issuer = issuer; }
    public String getAuthorizationEndpoint() { return authorizationEndpoint; }
    public void setAuthorizationEndpoint(final String authorizationEndpoint) { this.authorizationEndpoint = authorizationEndpoint; }
    public String getTokenEndpoint() { return tokenEndpoint; }
    public void setTokenEndpoint(final String tokenEndpoint) { this.tokenEndpoint = tokenEndpoint; }
    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(final String jwksUri) { this.jwksUri = jwksUri; }
    public String getClientId() { return clientId; }
    public void setClientId(final String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(final String clientSecret) { this.clientSecret = clientSecret; }
    public String getClientAuthMethod() { return clientAuthMethod; }
    public void setClientAuthMethod(final String clientAuthMethod) { this.clientAuthMethod = clientAuthMethod; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(final String redirectUri) { this.redirectUri = redirectUri; }
    public List<String> getScopes() { return scopes; }
    public void setScopes(final List<String> scopes) { this.scopes = scopes == null ? null : new ArrayList<>(scopes); }
}
