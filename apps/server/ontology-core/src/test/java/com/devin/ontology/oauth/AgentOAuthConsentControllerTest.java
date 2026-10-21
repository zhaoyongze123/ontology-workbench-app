package com.devin.ontology.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class AgentOAuthConsentControllerTest {
    @Test
    void consentFormsPostToAuthorizeEndpointWithinServletContext() {
        AgentClientRepository clients = mock(AgentClientRepository.class);
        when(clients.viewByClientId("client")).thenReturn(new AgentClientRepository.AppClientView(
                "record", "client", "Ontology", "https://app.example/callback",
                Set.of("profile"), true, Instant.EPOCH));
        AgentOAuthConsentController controller = new AgentOAuthConsentController(clients);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/oidc/app/consent");
        request.setContextPath("/api/v1");
        request.addParameter("client_id", "client");
        request.addParameter("scope", "profile");

        ResponseEntity<String> response = controller.consent(request);

        assertThat(response.getBody()).contains(
                "<form method='post' action='/api/v1/oidc/app/authorize'>");
        assertThat(response.getBody()).contains("action='/api/v1/oidc/app/authorize'");
        assertThat(response.getBody().split("action='/api/v1/oidc/app/authorize'", -1)).hasSize(3);
        assertThat(response.getBody()).doesNotContain("action='/oidc/app/authorize'");
    }
}
