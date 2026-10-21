package com.devin.ontology.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devin.ontology.security.LoginUserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;

class OpaqueOAuth2TokenGeneratorTest {
    private final OpaqueOAuth2TokenGenerator generator = new OpaqueOAuth2TokenGenerator();

    @Test
    void createsHighEntropyOpaqueAccessTokensWithFifteenMinuteMaximum() {
        Instant loginAt = Instant.now().minusSeconds(60);
        OAuth2TokenContext context = context(OAuth2TokenType.ACCESS_TOKEN, loginAt, null);

        OAuth2AccessToken first = (OAuth2AccessToken) generator.generate(context);
        OAuth2AccessToken second = (OAuth2AccessToken) generator.generate(context);

        assertEquals(43, first.getTokenValue().length());
        assertNotEquals(first.getTokenValue(), second.getTokenValue());
        assertTrue(Duration.between(first.getIssuedAt(), first.getExpiresAt()).compareTo(Duration.ofMinutes(15)) <= 0);
    }

    @Test
    void capsRefreshAndAccessTokensAtOriginalLoginPlusEightHours() {
        Instant loginAt = Instant.now().minus(Duration.ofHours(7).plus(Duration.ofMinutes(30)));
        OAuth2TokenContext accessContext = context(OAuth2TokenType.ACCESS_TOKEN, loginAt, null);
        OAuth2TokenContext refreshContext = context(OAuth2TokenType.REFRESH_TOKEN, loginAt, null);

        OAuth2AccessToken access = (OAuth2AccessToken) generator.generate(accessContext);
        OAuth2Token refresh = generator.generate(refreshContext);
        Instant absoluteExpiry = loginAt.plus(Duration.ofHours(8));

        assertTrue(!access.getExpiresAt().isAfter(absoluteExpiry));
        assertTrue(!refresh.getExpiresAt().isAfter(absoluteExpiry));
        assertTrue(Duration.between(refresh.getIssuedAt(), refresh.getExpiresAt()).compareTo(Duration.ofHours(1)) < 0);
    }

    @Test
    void authorizationCodeAlsoUsesHighEntropyRandomValue() {
        Instant loginAt = Instant.now();
        OAuth2AuthorizationCode code = (OAuth2AuthorizationCode) generator.generate(
                context(new OAuth2TokenType("code"), loginAt, null));

        assertEquals(43, code.getTokenValue().length());
        assertTrue(Duration.between(code.getIssuedAt(), code.getExpiresAt()).compareTo(Duration.ofMinutes(5)) <= 0);
    }

    private OAuth2TokenContext context(final OAuth2TokenType tokenType, final Instant loginAt,
            final OAuth2Authorization authorization) {
        OAuth2TokenContext context = mock(OAuth2TokenContext.class);
        when(context.getTokenType()).thenReturn(tokenType);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("profile", "ontology.read"));
        if (authorization != null) {
            when(context.getAuthorization()).thenReturn(authorization);
        } else {
            LoginUserPrincipal principal = new LoginUserPrincipal("user-1", "alice", "Alice",
                    false, 0, loginAt.toEpochMilli());
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, Set.of());
            when(context.getPrincipal()).thenReturn(authentication);
        }
        return context;
    }
}
