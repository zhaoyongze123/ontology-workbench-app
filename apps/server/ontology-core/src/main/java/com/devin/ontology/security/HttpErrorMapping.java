package com.devin.ontology.security;

import com.devin.ontology.common.exception.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** HTTP 边界唯一的稳定错误码到状态码映射；领域服务不依赖 HTTP。 */
public final class HttpErrorMapping {
    private HttpErrorMapping() { }

    public static HttpStatus status(final ApiErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID, VALIDATION_FAILED, ACCOUNT_ACTION_INVALID, PASSWORD_POLICY_VIOLATION,
                    GRAPH_IMPORT_INVALID, GRAPH_TYPE_MISMATCH
                    -> HttpStatus.BAD_REQUEST;
            case AUTH_REQUIRED, AUTH_INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED, SECURITY_CSRF_INVALID, SECURITY_ORIGIN_REJECTED -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND, GRAPH_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RESOURCE_CONFLICT, BUSINESS_RULE_CONFLICT, GRAPH_NAME_CONFLICT, GRAPH_HAS_DEPENDENCIES,
                    GRAPH_NODE_NAME_CONFLICT, GRAPH_NODE_HAS_DEPENDENCIES, GRAPH_RELATIONSHIP_CONFLICT,
                    GRAPH_RELATIONSHIP_HAS_DEPENDENCIES, PROJECT_HAS_DEPENDENCIES,
                    ACCOUNT_USERNAME_CONFLICT, ACCOUNT_LAST_ADMIN,
                    ACCOUNT_ALREADY_ACTIVATED, INVITATION_PENDING, PROJECT_ALREADY_MEMBER, PROJECT_OWNER_CONFLICT
                    -> HttpStatus.CONFLICT;
            case REQUEST_UNPROCESSABLE_ENTITY -> HttpStatus.UNPROCESSABLE_ENTITY;
            case QUERY_SYNTAX_INVALID, QUERY_READ_ONLY_REQUIRED, QUERY_INPUT_LIMIT_EXCEEDED, QUERY_RESULT_LIMIT_EXCEEDED,
                    QUERY_BUDGET_EXCEEDED, QUERY_EXECUTION_REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case REQUEST_METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case REQUEST_NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;
            case REQUEST_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case REQUEST_UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static ApiErrorCode forStatus(final int status) {
        return switch (status) {
            case 400 -> ApiErrorCode.REQUEST_INVALID;
            case 401 -> ApiErrorCode.AUTH_REQUIRED;
            case 403 -> ApiErrorCode.ACCESS_DENIED;
            case 404 -> ApiErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ApiErrorCode.REQUEST_METHOD_NOT_ALLOWED;
            case 406 -> ApiErrorCode.REQUEST_NOT_ACCEPTABLE;
            case 409 -> ApiErrorCode.BUSINESS_RULE_CONFLICT;
            case 413 -> ApiErrorCode.REQUEST_TOO_LARGE;
            case 415 -> ApiErrorCode.REQUEST_UNSUPPORTED_MEDIA_TYPE;
            case 422 -> ApiErrorCode.REQUEST_UNPROCESSABLE_ENTITY;
            case 429 -> ApiErrorCode.RATE_LIMITED;
            case 503 -> ApiErrorCode.DEPENDENCY_UNAVAILABLE;
            default -> ApiErrorCode.INTERNAL_ERROR;
        };
    }
}
