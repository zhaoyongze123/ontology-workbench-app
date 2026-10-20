package com.devin.ontology.common.aop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Method;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class LoggerAspectTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggerAspect.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsMetadataWithoutRequestOrResponseBodies() throws Throwable {
        String requestSecret = "request-secret-value";
        String responseToken = "response-access-token";
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/oidc/app/token");
        request.setRemoteAddr("192.0.2.10");
        request.setAttribute("ontology.requestId", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));

        ProceedingJoinPoint joinPoint = joinPoint(new Object[]{requestSecret}, Map.of("access_token", responseToken));
        new LoggerAspect().aroundAdvice(joinPoint);

        String output = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertTrue(output.contains("requestId=request-123"));
        assertTrue(output.contains("argCount=1"));
        assertFalse(output.contains(requestSecret));
        assertFalse(output.contains(responseToken));
        assertFalse(output.contains("access_token"));
    }

    @Test
    void logsFailureTypeWithoutExceptionMessage() throws Throwable {
        String secretMessage = "client_secret=never-log-this";
        appender.start();
        logger.addAppender(appender);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/api/v1/oidc/app/token"), new MockHttpServletResponse()));
        ProceedingJoinPoint joinPoint = joinPoint(new Object[0], null);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException(secretMessage));

        try {
            new LoggerAspect().aroundAdvice(joinPoint);
        } catch (IllegalStateException expected) {
            // Ошибка передаётся штатной обработке, но её текст в лог не попадает.
        }

        String output = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertTrue(output.contains("errorType=IllegalStateException"));
        assertFalse(output.contains(secretMessage));
    }

    @Test
    void sanitizesControlCharactersInLoggedRequestPath() throws Throwable {
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/graph/\tbroken\npath");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));

        new LoggerAspect().aroundAdvice(joinPoint(new Object[0], null));

        String output = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertTrue(output.contains("path=/api/v1/graph/_broken_path"));
        assertFalse(output.contains("broken\npath"));
    }

    private ProceedingJoinPoint joinPoint(final Object[] arguments, final Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = SampleController.class.getMethod("call", String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(SampleController.class);
        when(joinPoint.getTarget()).thenReturn(new SampleController());
        when(joinPoint.getArgs()).thenReturn(arguments);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    static class SampleController {
        public Object call(final String value) {
            return value;
        }
    }
}
