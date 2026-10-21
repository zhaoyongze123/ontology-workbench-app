package com.devin.ontology.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Bearer 资源错误维持 RFC 6750 challenge，不走普通 Problem Details 序列化。 */
class AgentBearerAuthenticationFilterTest {
    @Test
    void missingCredentialReturnsBareBearerChallenge() throws Exception {
        AgentAccessTokenService tokens = mock(AgentAccessTokenService.class);
        AgentBearerAuthenticationFilter filter = new AgentBearerAuthenticationFilter(tokens, new ObjectMapper());
        MockHttpServletResponse response = invoke(filter, null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("\"error\":\"unauthorized\"");
    }

    @Test
    void invalidCredentialReturnsInvalidTokenChallengeWithoutReflection() throws Exception {
        AgentAccessTokenService tokens = mock(AgentAccessTokenService.class);
        when(tokens.authenticate("sensitive-token-marker")).thenReturn(null);
        AgentBearerAuthenticationFilter filter = new AgentBearerAuthenticationFilter(tokens, new ObjectMapper());
        MockHttpServletResponse response = invoke(filter, "Bearer sensitive-token-marker");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
        assertThat(response.getContentAsString()).contains("invalid_token")
                .doesNotContain("sensitive-token-marker");
        verify(tokens).authenticate("sensitive-token-marker");
    }

    private MockHttpServletResponse invoke(final AgentBearerAuthenticationFilter filter, final String credential)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (credential != null) request.addHeader("Authorization", credential);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
