package com.devin.ontology.security;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.ApiErrorResponseWriter;
import com.devin.ontology.common.exception.ApiErrorResponseWriter.ApiFieldError;
import com.devin.ontology.common.exception.LegacyErrorAdapter;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.common.exception.BusinessException;
import com.devin.ontology.common.exception.ClientException;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.identity.AccountLifecycleController;
import com.devin.ontology.common.utils.r.ResultEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.transaction.TransactionTimedOutException;

/** 多用户 App 的统一 MVC 错误边界；OAuth 协议端点仍由专属处理器拥有。 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("multiuser")
public class MultiuserApiExceptionAdvice {
    private final ApiErrorResponseWriter writer;

    public MultiuserApiExceptionAdvice(final ApiErrorResponseWriter writer) {
        this.writer = writer;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handle(final Exception exception, final HttpServletRequest request) {
        Failure failure = classify(exception);
        failure = preserveLegacyAccountShape(failure, request);
        if (HttpErrorMapping.status(failure.code()).is5xxServerError()) {
            // Throwable message、SQL 与用户正文不进入日志；异常类型和 requestId 足以关联安全诊断。
            log.error("api request failed requestId={} errorCode={} exceptionType={}", writer.requestId(request),
                    failure.code().getValue(), exception.getClass().getSimpleName());
        }
        return writer.response(request, HttpErrorMapping.status(failure.code()), failure.code(), failure.legacyShape(),
                failure.legacyError(), failure.legacyNumericCode(), failure.fieldErrors(), failure.problemFields(),
                failure.headers());
    }

    /** 账号管理端点继续保留既有 JSON 错误 envelope，避免统一错误协议改变旧调用方契约。 */
    private Failure preserveLegacyAccountShape(final Failure failure, final HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod method)
                || !AccountLifecycleController.class.isAssignableFrom(method.getBeanType())) {
            return failure;
        }
        return Failure.shortError(failure.code(), LegacyErrorAdapterValue.forCode(failure.code()));
    }

    private Failure classify(final Exception exception) {
        if (exception instanceof OntologyApiException typed) {
            return Failure.api(typed.getErrorCode());
        }
        if (exception instanceof MethodArgumentNotValidException invalid) {
            return invalidFields(invalid.getBindingResult().getFieldErrors());
        }
        if (exception instanceof BindException invalid) {
            return invalidFields(invalid.getBindingResult().getFieldErrors());
        }
        if (exception instanceof ConstraintViolationException invalid) {
            List<ApiFieldError> fields = invalid.getConstraintViolations().stream()
                    .map(item -> new ApiFieldError(safeField(item.getPropertyPath().toString()),
                            "VALIDATION_FAILED", safeMessage(item.getMessage())))
                    .toList();
            return Failure.fields(ApiErrorCode.VALIDATION_FAILED, fields);
        }
        if (exception instanceof HandlerMethodValidationException) {
            return Failure.api(ApiErrorCode.VALIDATION_FAILED);
        }
        if (exception instanceof AuthenticationException) {
            return Failure.shortError(ApiErrorCode.AUTH_REQUIRED, "unauthorized");
        }
        if (exception instanceof AccessDeniedException) {
            return Failure.shortError(ApiErrorCode.ACCESS_DENIED, "forbidden");
        }
        if (exception instanceof HttpRequestMethodNotSupportedException method) {
            HttpHeaders headers = new HttpHeaders();
            if (method.getSupportedHttpMethods() != null) headers.setAllow(method.getSupportedHttpMethods());
            return Failure.withHeaders(ApiErrorCode.REQUEST_METHOD_NOT_ALLOWED, "method_not_allowed", headers);
        }
        if (exception instanceof HttpMediaTypeNotAcceptableException) {
            return Failure.shortError(ApiErrorCode.REQUEST_NOT_ACCEPTABLE, "not_acceptable");
        }
        if (exception instanceof HttpMediaTypeNotSupportedException media) {
            HttpHeaders headers = new HttpHeaders();
            if (!media.getSupportedMediaTypes().isEmpty()) {
                headers.set(HttpHeaders.ACCEPT, String.join(", ", media.getSupportedMediaTypes().stream()
                        .map(Objects::toString).toList()));
            }
            return Failure.withHeaders(ApiErrorCode.REQUEST_UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", headers);
        }
        if (exception instanceof MaxUploadSizeExceededException) {
            return Failure.shortError(ApiErrorCode.REQUEST_TOO_LARGE, "request_too_large");
        }
        if (exception instanceof MethodArgumentTypeMismatchException
                || exception instanceof MissingServletRequestParameterException
                || exception instanceof MissingServletRequestPartException
                || exception instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
            return Failure.shortError(ApiErrorCode.REQUEST_INVALID, "invalid_request");
        }
        if (exception instanceof NoResourceFoundException) {
            return Failure.shortError(ApiErrorCode.RESOURCE_NOT_FOUND, "not_found");
        }
        if (exception instanceof ResponseStatusException status) {
            ApiErrorCode code = HttpErrorMapping.forStatus(status.getStatusCode().value());
            return Failure.shortError(code, LegacyErrorAdapterValue.forCode(code));
        }
        if (exception instanceof DuplicateKeyException) {
            return Failure.api(ApiErrorCode.RESOURCE_CONFLICT, ResultEnum.DUPLICATE_KEY.getCode());
        }
        if (exception instanceof ValidateException validation) {
            if (validation.getApiErrorCode() != null) {
                return Failure.api(validation.getApiErrorCode(), validation.getErrCode());
            }
            return Failure.api(legacyValidationCode(validation.getErrCode()));
        }
        if (exception instanceof ClientException client) {
            ApiErrorCode code = legacyClientCode(client.getErrCode());
            return Failure.api(code, client.getErrCode());
        }
        if (exception instanceof BusinessException business) {
            ApiErrorCode code = legacyBusinessCode(business.getErrCode());
            return Failure.api(code, business.getErrCode());
        }
        if (exception instanceof DataAccessException dataAccess) {
            if (dataAccess instanceof TransientDataAccessException || hasConnectionSqlState(dataAccess)) {
                return Failure.api(ApiErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            String state = sqlState(dataAccess);
            if ("23505".equals(state) || "23503".equals(state)) {
                return Failure.api(ApiErrorCode.RESOURCE_CONFLICT);
            }
            // SQLSTATE 42601 只有在只读 Cypher 执行边界才代表用户查询语法错误。
            return Failure.api(ApiErrorCode.INTERNAL_ERROR);
        }
        if (exception instanceof TransactionTimedOutException) {
            return Failure.api(ApiErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        if (exception instanceof IllegalArgumentException) {
            // 未明确归类的参数异常属于程序/契约错误，不能按类名一概返回 400。
            return Failure.api(ApiErrorCode.INTERNAL_ERROR);
        }
        return Failure.api(ApiErrorCode.INTERNAL_ERROR);
    }

    private Failure invalidFields(final List<FieldError> fieldErrors) {
        Map<String, String> legacyFields = new LinkedHashMap<>();
        List<ApiFieldError> problemFields = new ArrayList<>();
        for (FieldError fieldError : fieldErrors) {
            String field = safeField(fieldError.getField());
            String message = safeFieldErrorMessage(fieldError);
            legacyFields.putIfAbsent(field, message);
            problemFields.add(new ApiFieldError(field, "VALIDATION_FAILED", message));
        }
        return Failure.fields(ApiErrorCode.VALIDATION_FAILED, problemFields, legacyFields);
    }

    private String safeFieldErrorMessage(final FieldError fieldError) {
        String message = safeMessage(fieldError.getDefaultMessage());
        Object rejectedValue = fieldError.getRejectedValue();
        if (rejectedValue == null) return message;
        String submittedValue = String.valueOf(rejectedValue);
        // Bean Validation 支持把实际提交值插入自定义消息；不得把密码、令牌等值回显给调用方。
        if (!submittedValue.isBlank() && message.contains(submittedValue)) return "输入值无效。";
        return message;
    }

    private ApiErrorCode legacyValidationCode(final Integer code) {
        if (code != null && code.equals(ResultEnum.CYPHER_IS_INVALID.getCode())) {
            return ApiErrorCode.QUERY_SYNTAX_INVALID;
        }
        return ApiErrorCode.REQUEST_INVALID;
    }

    private ApiErrorCode legacyClientCode(final Integer code) {
        if (code != null && code.equals(ResultEnum.AGE_ERROR.getCode())) return ApiErrorCode.DEPENDENCY_UNAVAILABLE;
        return ApiErrorCode.INTERNAL_ERROR;
    }

    private ApiErrorCode legacyBusinessCode(final Integer code) {
        if (code != null && code.equals(ResultEnum.DUPLICATE_KEY.getCode())) return ApiErrorCode.RESOURCE_CONFLICT;
        if (code != null && code.equals(ResultEnum.PARAM_IS_INVALID.getCode())) return ApiErrorCode.REQUEST_INVALID;
        if (code != null && code.equals(ResultEnum.CYPHER_IS_INVALID.getCode())) return ApiErrorCode.QUERY_SYNTAX_INVALID;
        if (code != null && code.equals(ResultEnum.ORGANIZATION_ERROR.getCode())) return ApiErrorCode.PROJECT_HAS_DEPENDENCIES;
        if (code != null && (code.equals(ResultEnum.GRAPH_ERROR.getCode()) || code.equals(ResultEnum.NODE_ERROR.getCode())
                || code.equals(ResultEnum.RELATIONSHIP_ERROR.getCode()))) return ApiErrorCode.INTERNAL_ERROR;
        return ApiErrorCode.BUSINESS_RULE_CONFLICT;
    }

    private boolean hasConnectionSqlState(final Throwable failure) {
        String state = sqlState(failure);
        return state != null && state.startsWith("08");
    }

    private String sqlState(final Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }

    private String safeField(final String field) {
        if (field == null) return "field";
        String value = field.replaceAll("[^A-Za-z0-9_.\\[\\]-]", "_");
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private String safeMessage(final String message) {
        if (message == null || message.isBlank()) return "输入值无效。";
        String value = message.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").trim();
        return value.length() > 240 ? value.substring(0, 240) : value;
    }

    private record Failure(ApiErrorCode code, LegacyErrorAdapter.Shape legacyShape, String legacyError,
            Integer legacyNumericCode, Map<String, String> fieldErrors, List<ApiFieldError> problemFields,
            HttpHeaders headers) {
        static Failure api(final ApiErrorCode code) {
            return api(code, code.getLegacyNumericCode());
        }
        static Failure api(final ApiErrorCode code, final Integer numericCode) {
            return new Failure(code, LegacyErrorAdapter.Shape.API_RESULT, null, numericCode, null, null, null);
        }
        static Failure shortError(final ApiErrorCode code, final String legacyError) {
            return new Failure(code, LegacyErrorAdapter.Shape.ERROR_FIELD, legacyError, null, null, null, null);
        }
        static Failure withHeaders(final ApiErrorCode code, final String legacyError, final HttpHeaders headers) {
            return new Failure(code, LegacyErrorAdapter.Shape.ERROR_FIELD, legacyError, null, null, null, headers);
        }
        static Failure fields(final ApiErrorCode code, final List<ApiFieldError> fields) {
            return fields(code, fields, Map.of());
        }
        static Failure fields(final ApiErrorCode code, final List<ApiFieldError> fields,
                final Map<String, String> legacyFields) {
            return new Failure(code, LegacyErrorAdapter.Shape.API_RESULT, null, code.getLegacyNumericCode(),
                    legacyFields, fields, null);
        }
    }

    private static final class LegacyErrorAdapterValue {
        private LegacyErrorAdapterValue() { }
        static String forCode(final ApiErrorCode code) {
            return switch (code) {
                case AUTH_REQUIRED, AUTH_INVALID_CREDENTIALS -> "unauthorized";
                case ACCESS_DENIED, SECURITY_CSRF_INVALID, SECURITY_ORIGIN_REJECTED -> "forbidden";
                case RESOURCE_NOT_FOUND, GRAPH_NOT_FOUND -> "not_found";
                case RESOURCE_CONFLICT, BUSINESS_RULE_CONFLICT, ACCOUNT_USERNAME_CONFLICT, ACCOUNT_LAST_ADMIN,
                        GRAPH_NAME_CONFLICT, GRAPH_HAS_DEPENDENCIES, GRAPH_NODE_NAME_CONFLICT,
                        GRAPH_NODE_HAS_DEPENDENCIES, GRAPH_RELATIONSHIP_CONFLICT,
                        GRAPH_RELATIONSHIP_HAS_DEPENDENCIES, PROJECT_HAS_DEPENDENCIES, ACCOUNT_ALREADY_ACTIVATED,
                        INVITATION_PENDING, PROJECT_ALREADY_MEMBER, PROJECT_OWNER_CONFLICT
                        -> "conflict";
                case REQUEST_METHOD_NOT_ALLOWED -> "method_not_allowed";
                case REQUEST_NOT_ACCEPTABLE -> "not_acceptable";
                case REQUEST_TOO_LARGE -> "request_too_large";
                case REQUEST_UNSUPPORTED_MEDIA_TYPE -> "unsupported_media_type";
                case RATE_LIMITED -> "too_many_requests";
                case DEPENDENCY_UNAVAILABLE -> "request_unavailable";
                case REQUEST_INVALID, VALIDATION_FAILED, ACCOUNT_ACTION_INVALID, PASSWORD_POLICY_VIOLATION,
                        REQUEST_UNPROCESSABLE_ENTITY, GRAPH_IMPORT_INVALID, GRAPH_TYPE_MISMATCH -> "invalid_request";
                default -> "request_failed";
            };
        }
    }
}
