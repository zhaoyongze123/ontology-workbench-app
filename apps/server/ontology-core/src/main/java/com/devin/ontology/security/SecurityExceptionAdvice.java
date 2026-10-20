package com.devin.ontology.security;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 安全异常优先转换为统一 HTTP 状态，不记录请求参数或登录凭据。 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("multiuser")
public class SecurityExceptionAdvice {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> authenticationFailure(final AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> accessDenied(final AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
    }
}
