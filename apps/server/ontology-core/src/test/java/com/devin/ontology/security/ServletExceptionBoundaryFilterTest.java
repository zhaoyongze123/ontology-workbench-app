package com.devin.ontology.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ServletExceptionBoundaryFilterTest {
    private static final String SECRET = "fake-secret-must-never-appear-in-response-or-log";
    private final Logger logger = (Logger) LoggerFactory.getLogger(ServletExceptionBoundaryFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void cleanup() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void convertsUnhandledRuntimeFailureToFixed503AndLogsOnlyTypeAndRequestId() throws Exception {
        SecurityAuditRecorder audit = mock(SecurityAuditRecorder.class);
        ServletExceptionBoundaryFilter filter = new ServletExceptionBoundaryFilter(audit);
        appender.setContext((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = request("req-runtime-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { throw new IllegalStateException(SECRET); });

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"request_unavailable\"}");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(audit).recordResponseFailure(request, response);
        assertNoSecretInLogs();
    }

    @Test
    void convertsUnhandledServletFailureWithoutLeakingCauseText() throws Exception {
        ServletExceptionBoundaryFilter filter = new ServletExceptionBoundaryFilter(mock(SecurityAuditRecorder.class));
        appender.setContext((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("req-servlet-123"), response,
                (req, res) -> { throw new ServletException("outer", new IllegalArgumentException(SECRET)); });

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain(SECRET);
        assertNoSecretInLogs();
    }

    @Test
    void clearsPartialClientErrorBodyHeadersAndLengthBeforeWritingFixed503() throws Exception {
        SecurityAuditRecorder audit = mock(SecurityAuditRecorder.class);
        ServletExceptionBoundaryFilter filter = new ServletExceptionBoundaryFilter(audit);
        appender.setContext((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = request("req-partial-400");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            MockHttpServletResponse partial = (MockHttpServletResponse) res;
            partial.setStatus(400);
            partial.setContentLength(9000);
            partial.setHeader("X-Stale", "stale");
            partial.getWriter().write(SECRET);
            throw new IllegalStateException(SECRET);
        });

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"request_unavailable\"}")
                .doesNotContain(SECRET);
        assertThat(response.getHeader("Content-Length")).isNull();
        assertThat(response.getHeader("X-Stale")).isNull();
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("req-partial-400");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        verify(audit).recordResponseFailure(request, response);
        assertNoSecretInLogs();
    }

    @Test
    void preservesNormalClientErrorsAndCannotRewriteCommittedResponse() throws Exception {
        SecurityAuditRecorder audit = mock(SecurityAuditRecorder.class);
        ServletExceptionBoundaryFilter filter = new ServletExceptionBoundaryFilter(audit);
        MockHttpServletResponse unauthorized = new MockHttpServletResponse();
        filter.doFilter(request("req-401"), unauthorized, (req, res) -> {
            MockHttpServletResponse response = (MockHttpServletResponse) res;
            response.setStatus(401);
            response.setHeader("X-Original", "unauthorized");
            response.getWriter().write("unauthorized-body");
        });
        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat(unauthorized.getHeader("X-Original")).isEqualTo("unauthorized");
        assertThat(unauthorized.getContentAsString()).isEqualTo("unauthorized-body");

        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        filter.doFilter(request("req-403"), forbidden, (req, res) -> {
            MockHttpServletResponse response = (MockHttpServletResponse) res;
            response.setStatus(403);
            response.setHeader("X-Original", "forbidden");
            response.getWriter().write("forbidden-body");
        });
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(forbidden.getHeader("X-Original")).isEqualTo("forbidden");
        assertThat(forbidden.getContentAsString()).isEqualTo("forbidden-body");

        MockHttpServletResponse committed = new MockHttpServletResponse();
        committed.getWriter().write("already-sent");
        committed.flushBuffer();
        filter.doFilter(request("req-committed"), committed,
                (req, res) -> { throw new IllegalStateException(SECRET); });
        assertThat(committed.isCommitted()).isTrue();
        assertThat(committed.getContentAsString()).isEqualTo("already-sent");
        verifyNoInteractions(audit);
    }

    private MockHttpServletRequest request(final String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, requestId);
        return request;
    }

    private void assertNoSecretInLogs() {
        String output = appender.list.stream().map(event -> event.getFormattedMessage() + " "
                        + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage()))
                .reduce("", (left, right) -> left + right);
        assertThat(output).contains("requestId=").doesNotContain(SECRET);
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }
}
