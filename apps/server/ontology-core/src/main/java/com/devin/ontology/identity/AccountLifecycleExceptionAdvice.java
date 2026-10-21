package com.devin.ontology.identity;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 账号接口使用固定错误码和 HTTP 状态，不把 SQL、输入或 cause 细节返回给调用者。 */
@RestControllerAdvice(assignableTypes = AccountLifecycleController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("multiuser")
public class AccountLifecycleExceptionAdvice {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> accountFailure(final ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String error = switch (status) {
            case BAD_REQUEST -> "invalid_request";
            case NOT_FOUND -> "not_found";
            case CONFLICT -> "conflict";
            case TOO_MANY_REQUESTS -> "too_many_requests";
            default -> "request_failed";
        };
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
