package com.devin.ontology.oauth;

import com.devin.ontology.security.LoginUserPrincipal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/** 为 App 签发 256 位随机、不含用户信息的 opaque code/access/refresh token。 */
public final class OpaqueOAuth2TokenGenerator implements OAuth2TokenGenerator<OAuth2Token> {
    static final String LOGIN_AT_ATTRIBUTE = "ontology.loginAt";
    static final String ABSOLUTE_EXPIRY_ATTRIBUTE = "ontology.absoluteExpiresAt";
    static final Duration MAX_GRANT_LIFETIME = Duration.ofHours(8);

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public OAuth2Token generate(final OAuth2TokenContext context) {
        OAuth2TokenType tokenType = context.getTokenType();
        if (tokenType == null) return null;
        Instant issuedAt = Instant.now();
        Instant absoluteExpiry = absoluteExpiry(context);
        String value = randomValue();
        return switch (tokenType.getValue()) {
            case OAuth2ParameterNames.CODE -> new org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode(
                    value, issuedAt, min(issuedAt.plus(Duration.ofMinutes(5)), absoluteExpiry));
            case "access_token" -> new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value,
                    issuedAt, min(issuedAt.plus(Duration.ofMinutes(15)), absoluteExpiry),
                    context.getAuthorizedScopes() == null ? Set.of() : context.getAuthorizedScopes());
            case "refresh_token" -> new OAuth2RefreshToken(value, issuedAt,
                    min(issuedAt.plus(Duration.ofHours(8)), absoluteExpiry));
            default -> null;
        };
    }

    private Instant absoluteExpiry(final OAuth2TokenContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization != null) {
            Instant expiry = authorization.getAttribute(ABSOLUTE_EXPIRY_ATTRIBUTE);
            if (expiry != null) return expiry;
        }
        Authentication authentication = context.getPrincipal();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUserPrincipal principal) {
            Instant loginAt = Instant.ofEpochMilli(principal.loginAtEpochMillis());
            return loginAt.plus(MAX_GRANT_LIFETIME);
        }
        return Instant.now();
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Instant min(final Instant left, final Instant right) {
        return left.isBefore(right) ? left : right;
    }
}
