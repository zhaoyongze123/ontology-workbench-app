package com.devin.ontology.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
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
