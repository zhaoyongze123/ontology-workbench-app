package com.devin.ontology.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerLogTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void cleanUp() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsExceptionMetadataWithoutMessagesOrDatabaseValues() {
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cypher/query");
        request.setAttribute("ontology.requestId", "server-request-id");

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        handler.handleBusinessException(request, new BusinessException("secret-cypher-marker"));
        handler.handleClientException(request, new ClientException("secret-client-marker"));
        handler.handleDuplicateKeyException(request, new ValidateException("secret-validation-marker"));
        handler.handleDuplicateKeyException(request,
                new DuplicateKeyException("Key (name)=(secret-database-value) already exists"));

        String output = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(output).contains("requestId=server-request-id", "path=/api/v1/cypher/query",
                "exceptionType=BusinessException", "exceptionType=ClientException",
                "exceptionType=ValidateException", "exceptionType=DuplicateKeyException");
        assertThat(output).doesNotContain("secret-cypher-marker", "secret-client-marker",
                "secret-validation-marker", "secret-database-value");
    }

    @Test
    void sanitizesControlCharactersInRequestMetadata() {
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/line\nbreak");
        request.setAttribute("ontology.requestId", "request\r\nid");

        new GlobalExceptionHandler().handleException(request, new IllegalStateException("must-not-be-logged"));

        String output = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(output).contains("request__id", "path=/api/v1/line_break")
                .doesNotContain("request\r\nid", "line\nbreak", "must-not-be-logged");
    }
}
