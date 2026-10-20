package com.devin.ontology.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityAuditRecorderTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(SecurityAuditRecorder.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void cleanUp() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void writesOnlySafeMetadataForAccessDenial() {
        SecurityAuditEventWriter writer = mock(SecurityAuditEventWriter.class);
        SecurityAuditRecorder recorder = new SecurityAuditRecorder(writer);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/graph/query");
        request.addHeader("Authorization", "Bearer bearer-secret-value");
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, "server-request-id");
        request.setAttribute(RequestCorrelationFilter.CLIENT_ADDRESS_ATTRIBUTE, "192.0.2.10");
        request.setAttribute(SecurityAuditRecorder.ACTOR_USER_ID_ATTRIBUTE, "internal-user-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        recorder.recordResponseFailure(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(writer).appendFailure(eq("SECURITY_ACCESS_DENIED"), eq("internal-user-id"), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("requestId", "server-request-id")
                .containsEntry("method", "POST").containsEntry("path", "/api/v1/graph/query")
                .containsEntry("status", HttpServletResponse.SC_FORBIDDEN)
                .containsEntry("peer", "192.0.2.10");
        assertThat(metadata.getValue().toString()).doesNotContain("bearer-secret-value", "Authorization");
    }

    @Test
    void recordsOAuthProtocolRejectionsWithoutRequestParameters() {
        SecurityAuditEventWriter writer = mock(SecurityAuditEventWriter.class);
        SecurityAuditRecorder recorder = new SecurityAuditRecorder(writer);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/oidc/app/authorize");
        request.setQueryString("code=authorization-code-secret");
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, "server-request-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

        recorder.recordResponseFailure(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(writer).appendFailure(eq("SECURITY_OAUTH_PROTOCOL_REJECTED"), eq(null), metadata.capture());
        assertThat(metadata.getValue().toString()).doesNotContain("authorization-code-secret", "code=");
    }

    @Test
    void recordsRateLimitMetadataWithoutCredentialValues() {
        SecurityAuditEventWriter writer = mock(SecurityAuditEventWriter.class);
        SecurityAuditRecorder recorder = new SecurityAuditRecorder(writer);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/oidc/app/token");
        request.addParameter("client_secret", "client-secret-marker");
        request.addParameter("refresh_token", "refresh-token-marker");
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, "server-request-id");
        request.setAttribute(RequestCorrelationFilter.CLIENT_ADDRESS_ATTRIBUTE, "192.0.2.12");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(429);

        recorder.recordResponseFailure(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(writer).appendFailure(eq("SECURITY_RATE_LIMITED"), eq(null), metadata.capture());
        assertThat(metadata.getValue().toString()).contains("server-request-id", "/api/v1/oidc/app/token", "429")
                .doesNotContain("client-secret-marker", "refresh-token-marker", "client_secret");
    }

    @Test
    void auditStorageFailureDoesNotChangeResponseOrLogExceptionText() {
        SecurityAuditEventWriter writer = mock(SecurityAuditEventWriter.class);
        doThrow(new IllegalStateException("secret response body must not leak"))
                .when(writer).appendFailure(eq("SECURITY_AUTHENTICATION_FAILURE"), eq(null), anyMap());
        SecurityAuditRecorder recorder = new SecurityAuditRecorder(writer);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, "request-2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        appender.start();
        logger.addAppender(appender);

        recorder.recordResponseFailure(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        String output = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(output).contains("failureType=IllegalStateException")
                .doesNotContain("secret response body must not leak");
    }
}
