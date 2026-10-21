package com.devin.ontology.oidc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** 仅在服务端 session 保存严格限定的 Workbench App 同源回跳路径。 */
public final class WorkbenchOAuthReturnPath {
    public static final String SESSION_ATTRIBUTE = WorkbenchOAuthReturnPath.class.getName() + ".returnPath";
    static final String QUERY_PARAMETER = "oauth_return";
    static final String AUTHORIZATION_PATH = "/api/v1/oidc/app/authorize";
    static final int MAX_LENGTH = 2048;

    private WorkbenchOAuthReturnPath() { }

    static OAuth2AuthorizationRequestResolver wrap(final OAuth2AuthorizationRequestResolver delegate) {
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(final HttpServletRequest request) {
                return resolveWithReturnPath(delegate, request, null);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(final HttpServletRequest request,
                    final String clientRegistrationId) {
                return resolveWithReturnPath(delegate, request, clientRegistrationId);
            }
        };
    }

    private static OAuth2AuthorizationRequest resolveWithReturnPath(
            final OAuth2AuthorizationRequestResolver delegate, final HttpServletRequest request,
            final String registrationId) {
        String path = request.getServletPath();
        if (WorkbenchOidcConfigurer.AUTHORIZATION_ENTRY.equals(path)) {
            HttpSession session = request.getSession(false);
            if (session != null) session.removeAttribute(SESSION_ATTRIBUTE);

            String candidate = request.getParameter(QUERY_PARAMETER);
            if (candidate != null) {
                String safe = validate(candidate);
                // 非法回跳不启动上游授权；任何旧回跳已先清除，不能在后续登录中复用。
                if (safe == null) return null;
                request.getSession(true).setAttribute(SESSION_ATTRIBUTE, safe);
            }
        }
        return registrationId == null ? delegate.resolve(request) : delegate.resolve(request, registrationId);
    }

    static String validate(final String candidate) {
        if (candidate == null || candidate.length() > MAX_LENGTH || candidate.isBlank()
                || candidate.startsWith("//")
                || candidate.chars().anyMatch(character -> Character.isISOControl(character) || character == '\\')) {
            return null;
        }
        try {
            URI uri = URI.create(candidate);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null
                    || !AUTHORIZATION_PATH.equals(uri.getRawPath()) || uri.getRawQuery() == null
                    || uri.getRawQuery().isBlank()) {
                return null;
            }
            String lowerQuery = uri.getRawQuery().toLowerCase(Locale.ROOT);
            if (lowerQuery.matches("(?s).*%(?:0[0-9a-f]|1[0-9a-f]|7f|5c).*")) return null;
            for (String pair : uri.getRawQuery().split("&")) {
                int separator = pair.indexOf('=');
                String rawName = separator < 0 ? pair : pair.substring(0, separator);
                String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (name.equals("access_token") || name.equals("id_token") || name.equals("refresh_token")
                        || name.equals("client_secret") || name.equals("password") || name.equals("code")) {
                    return null;
                }
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
