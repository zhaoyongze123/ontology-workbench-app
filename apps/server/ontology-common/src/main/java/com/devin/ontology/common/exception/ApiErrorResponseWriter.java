package com.devin.ontology.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Profile;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 普通業務 API 在 MVC 與 Filter 邊界共用的錯誤輸出器。 */
@Component
@Profile("multiuser")
public final class ApiErrorResponseWriter {
    private final ObjectMapper mapper;
    private final MessageSource messages;

    public ApiErrorResponseWriter(final ObjectMapper mapper, final MessageSource messages) {
        this.mapper = mapper;
        this.messages = messages;
    }

    public ResponseEntity<Object> response(final HttpServletRequest request, final HttpStatus status,
            final ApiErrorCode code, final LegacyErrorAdapter.Shape legacyShape, final String legacyError,
            final Integer numericCode, final Map<String, String> fieldErrors,
            final List<ApiFieldError> problemFields, final HttpHeaders extraHeaders) {
        String requestId = requestId(request);
        HttpHeaders headers = commonHeaders(requestId);
        if (extraHeaders != null) extraHeaders.forEach((name, values) -> headers.put(name, new ArrayList<>(values)));

        Object body;
        if (acceptsProblemDetails(request)) {
            ProblemDetail problem = ProblemDetail.forStatus(status);
            problem.setType(URI.create("urn:ontology:problem:" + kebab(code.getValue())));
            problem.setTitle(message(code.getTitleMessageKey(), code.name()));
            problem.setDetail(message(code.getDetailMessageKey(), "请求处理失败，请稍后重试。"));
            problem.setInstance(URI.create("urn:uuid:" + requestId));
            problem.setProperty("errorCode", code.getValue());
            problem.setProperty("requestId", requestId);
            if (problemFields != null && !problemFields.isEmpty()) problem.setProperty("errors", problemFields);
            headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            body = problem;
        } else {
            body = LegacyErrorAdapter.adapt(legacyShape, code,
                    numericCode == null ? code.getLegacyNumericCode() : numericCode,
                    message(code.getDetailMessageKey(), "请求处理失败，请稍后重试。"), legacyError, fieldErrors);
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    public void write(final HttpServletRequest request, final HttpServletResponse response,
            final HttpStatus status, final ApiErrorCode code, final LegacyErrorAdapter.Shape legacyShape,
            final String legacyError, final HttpHeaders extraHeaders) throws IOException {
        if (response.isCommitted()) return;
        ResponseEntity<Object> rendered = response(request, status, code, legacyShape, legacyError,
                null, null, null, extraHeaders);
        response.setStatus(rendered.getStatusCode().value());
        response.setCharacterEncoding("UTF-8");
        rendered.getHeaders().forEach((name, values) -> {
            if (HttpHeaders.VARY.equalsIgnoreCase(name)) {
                values.forEach(value -> response.addHeader(name, value));
            } else {
                values.forEach(value -> response.setHeader(name, value));
            }
        });
        mapper.writeValue(response.getWriter(), rendered.getBody());
    }

    public String requestId(final HttpServletRequest request) {
        Object existing = request.getAttribute(ApiRequestContext.REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String value && StringUtils.hasText(value)) return value;
        String generated = UUID.randomUUID().toString();
        request.setAttribute(ApiRequestContext.REQUEST_ID_ATTRIBUTE, generated);
        return generated;
    }

    private HttpHeaders commonHeaders(final String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiRequestContext.REQUEST_ID_HEADER, requestId);
        headers.setCacheControl("no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.add(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        return headers;
    }

    private boolean acceptsProblemDetails(final HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (!StringUtils.hasText(accept)) return false;
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(type -> MediaType.APPLICATION_PROBLEM_JSON.getType().equalsIgnoreCase(type.getType())
                            && MediaType.APPLICATION_PROBLEM_JSON.getSubtype().equalsIgnoreCase(type.getSubtype())
                            && type.getQualityValue() > 0);
        } catch (IllegalArgumentException malformedAccept) {
            return false;
        }
    }

    private String message(final String key, final String fallback) {
        Locale locale = LocaleContextHolder.getLocale();
        return messages.getMessage(key, null, fallback, locale);
    }

    private String kebab(final String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public record ApiFieldError(String field, String code, String message) { }
}
