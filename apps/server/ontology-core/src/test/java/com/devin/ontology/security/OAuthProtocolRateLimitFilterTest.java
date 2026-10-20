package com.devin.ontology.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OAuthProtocolRateLimitFilterTest {
    @Test
    void blockedTokenRequestGetsGeneric429WithoutCallingProtocolEndpoint() throws Exception {
        OAuthProtocolRequestLimiter limiter = mock(OAuthProtocolRequestLimiter.class);
        when(limiter.allowRequest("OAUTH_TOKEN", "test-client", "203.0.113.9")).thenReturn(false);
        OAuthProtocolRateLimitFilter filter = new OAuthProtocolRateLimitFilter(limiter,
                new TrustedClientAddressResolver(Set.of()), new ObjectMapper(), 60);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContextPath("/api/v1");
        request.setRequestURI("/api/v1/oidc/app/token");
        request.addParameter("client_id", "test-client");
        request.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("too_many_requests").doesNotContain("test-client");
        assertThat(chain.getRequest()).isNull();
        verify(limiter).allowRequest("OAUTH_TOKEN", "test-client", "203.0.113.9");
    }
}
