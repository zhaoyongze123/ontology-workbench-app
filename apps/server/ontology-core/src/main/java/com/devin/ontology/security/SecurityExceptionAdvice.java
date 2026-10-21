package com.devin.ontology.security;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /** HTTP 协议格式错误必须保持 4xx，不能被旧全局 catch-all 改写成服务器错误。 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalidRequest(final Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> methodNotAllowed(
            final HttpRequestMethodNotSupportedException exception) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.getSupportedHttpMethods() != null) headers.setAllow(exception.getSupportedHttpMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers)
                .body(Map.of("error", "method_not_allowed"));
    }
}
