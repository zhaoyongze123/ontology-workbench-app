package com.devin.ontology.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class WorkbenchOidcConfigurerTest {
    @Test
    void resolverAlwaysAddsS256PkceAndUsesConfiguredPublicCallback() {
        WorkbenchOidcProperties properties = validProperties();
        new WorkbenchOidcConfiguration(properties);
        InMemoryClientRegistrationRepository registrations = (InMemoryClientRegistrationRepository)
                new WorkbenchOidcConfiguration(properties).workbenchClientRegistrationRepository(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/workbench");
        request.setServletPath("/oauth2/authorization/workbench");
        OAuth2AuthorizationRequest authorization = WorkbenchOidcConfigurer.pkceResolver(registrations).resolve(request);

        assertNotNull(authorization);
        assertEquals("S256", authorization.getAdditionalParameters().get("code_challenge_method"));
        assertTrue(authorization.getAdditionalParameters().get("code_challenge").toString().length() >= 40);
        assertEquals("https://gateway.example/oidc/callback", authorization.getRedirectUri());
        assertTrue(authorization.getAdditionalParameters().containsKey("nonce"));
    }

    @Test
    void resolverPersistsOnlyValidatedSameOriginAppReturnAndClearsStaleValueWhenAbsent() {
        WorkbenchOidcProperties properties = validProperties();
        InMemoryClientRegistrationRepository registrations = (InMemoryClientRegistrationRepository)
                new WorkbenchOidcConfiguration(properties).workbenchClientRegistrationRepository(properties);
        var resolver = WorkbenchOAuthReturnPath.wrap(WorkbenchOidcConfigurer.pkceResolver(registrations));
        String allowed = "/api/v1/oidc/app/authorize?client_id=demo&redirect_uri=https%3A%2F%2Fworkbench.example%2Fdone"
                + "&response_type=code&code_challenge=challenge&state=opaque";

        MockHttpServletRequest request = authorizationRequest();
        request.setParameter(WorkbenchOAuthReturnPath.QUERY_PARAMETER, allowed);
        assertNotNull(resolver.resolve(request));
        assertEquals(allowed, request.getSession(false).getAttribute(WorkbenchOAuthReturnPath.SESSION_ATTRIBUTE));

        MockHttpServletRequest nextLogin = authorizationRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WorkbenchOAuthReturnPath.SESSION_ATTRIBUTE, allowed);
        nextLogin.setSession(session);
        assertNotNull(resolver.resolve(nextLogin));
        assertNull(session.getAttribute(WorkbenchOAuthReturnPath.SESSION_ATTRIBUTE));
    }

    @Test
    void resolverRejectsAbsoluteProtocolRelativeWrongPathControlAndCredentialReturnValues() {
        for (String candidate : List.of("https://evil.example/path", "//evil.example/path",
                "/api/v1/other?x=1", "/api/v1/oidc/app/authorize?access_token=secret",
                "/api/v1/oidc/app/authorize?code=credential", "/api/v1/oidc/app/authorize?x=\\\\evil")) {
            assertNull(WorkbenchOAuthReturnPath.validate(candidate), candidate);
        }
        assertNull(WorkbenchOAuthReturnPath.validate("/api/v1/oidc/app/authorize?x=" + "x".repeat(WorkbenchOAuthReturnPath.MAX_LENGTH)));
        assertNull(WorkbenchOAuthReturnPath.validate("/api/v1/oidc/app/authorize?x=bad\nvalue"));
    }

    private static MockHttpServletRequest authorizationRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/workbench");
        request.setServletPath(WorkbenchOidcConfigurer.AUTHORIZATION_ENTRY);
        return request;
    }

    @Test
    void rejectsNonHttpsEndpointsAndUnregisteredCallbackShapes() {
        WorkbenchOidcProperties invalidHttps = validProperties();
        invalidHttps.setJwksUri("http://gateway.example/jwks");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, invalidHttps::validate);

        WorkbenchOidcProperties invalidRedirect = validProperties();
        invalidRedirect.setRedirectUri("https://gateway.example/callback");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, invalidRedirect::validate);
    }

    private static WorkbenchOidcProperties validProperties() {
        WorkbenchOidcProperties properties = new WorkbenchOidcProperties();
        properties.setIssuer("https://app.workbench.aos.work");
        properties.setAuthorizationEndpoint("https://app.workbench.aos.work/oidc/authorize");
        properties.setTokenEndpoint("https://app.workbench.aos.work/basilcraw/api/oidc/token");
        properties.setJwksUri("https://app.workbench.aos.work/basilcraw/api/oidc/jwks");
        properties.setClientId("fixture-client");
        properties.setClientSecret("fixture-secret");
        properties.setClientAuthMethod("post");
        properties.setRedirectUri("https://gateway.example/oidc/callback");
        properties.setScopes(List.of("openid", "profile", "email"));
        return properties;
    }
}
