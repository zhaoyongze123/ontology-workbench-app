package com.devin.ontology.common.exception;

import lombok.Getter;

/**
 * Ontology 普通业务 API 的稳定错误目录。
 * HTTP 状态由 HTTP 边界映射，业务服务只使用此目录表达失败原因。
 */
@Getter
public enum ApiErrorCode {
    REQUEST_INVALID("REQUEST_INVALID", "problem.request.title", "problem.request.invalid", 4002),
    VALIDATION_FAILED("VALIDATION_FAILED", "problem.request.title", "problem.validation.failed", 4002),
    REQUEST_UNPROCESSABLE_ENTITY("REQUEST_UNPROCESSABLE_ENTITY", "problem.request.title", "problem.request.unprocessable", 4002),
    AUTH_REQUIRED("AUTH_REQUIRED", "problem.auth.title", "problem.auth.required", 1000),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "problem.auth.title", "problem.auth.invalidCredentials", 1000),
    ACCESS_DENIED("ACCESS_DENIED", "problem.permission.title", "problem.permission.denied", 1000),
    SECURITY_CSRF_INVALID("SECURITY_CSRF_INVALID", "problem.permission.title", "problem.permission.csrf", 1000),
    SECURITY_ORIGIN_REJECTED("SECURITY_ORIGIN_REJECTED", "problem.permission.title", "problem.permission.origin", 1000),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "problem.resource.title", "problem.resource.notFound", 5001),
    RESOURCE_CONFLICT("RESOURCE_CONFLICT", "problem.conflict.title", "problem.conflict.resource", 5002),
    BUSINESS_RULE_CONFLICT("BUSINESS_RULE_CONFLICT", "problem.conflict.title", "problem.conflict.businessRule", 5001),
    GRAPH_NOT_FOUND("GRAPH_NOT_FOUND", "problem.resource.title", "problem.graph.notFound", 5001),
    GRAPH_NAME_CONFLICT("GRAPH_NAME_CONFLICT", "problem.conflict.title", "problem.graph.nameConflict", 5002),
    GRAPH_HAS_DEPENDENCIES("GRAPH_HAS_DEPENDENCIES", "problem.conflict.title", "problem.graph.hasDependencies", 5001),
    GRAPH_NODE_NAME_CONFLICT("GRAPH_NODE_NAME_CONFLICT", "problem.conflict.title", "problem.graph.nodeNameConflict", 5002),
    GRAPH_NODE_HAS_DEPENDENCIES("GRAPH_NODE_HAS_DEPENDENCIES", "problem.conflict.title", "problem.graph.nodeHasDependencies", 5001),
    GRAPH_RELATIONSHIP_CONFLICT("GRAPH_RELATIONSHIP_CONFLICT", "problem.conflict.title", "problem.graph.relationshipConflict", 5002),
    GRAPH_RELATIONSHIP_HAS_DEPENDENCIES("GRAPH_RELATIONSHIP_HAS_DEPENDENCIES", "problem.conflict.title", "problem.graph.relationshipHasDependencies", 5001),
    GRAPH_IMPORT_INVALID("GRAPH_IMPORT_INVALID", "problem.request.title", "problem.graph.importInvalid", 4002),
    GRAPH_TYPE_MISMATCH("GRAPH_TYPE_MISMATCH", "problem.request.title", "problem.graph.typeMismatch", 4002),
    PROJECT_HAS_DEPENDENCIES("PROJECT_HAS_DEPENDENCIES", "problem.conflict.title", "problem.project.hasDependencies", 5001),
    ACCOUNT_USERNAME_CONFLICT("ACCOUNT_USERNAME_CONFLICT", "problem.conflict.title", "problem.account.usernameConflict", 5002),
    ACCOUNT_LAST_ADMIN("ACCOUNT_LAST_ADMIN", "problem.conflict.title", "problem.account.lastAdmin", 5001),
    ACCOUNT_ALREADY_ACTIVATED("ACCOUNT_ALREADY_ACTIVATED", "problem.conflict.title", "problem.account.alreadyActivated", 5001),
    ACCOUNT_ACTION_INVALID("ACCOUNT_ACTION_INVALID", "problem.request.title", "problem.account.actionInvalid", 4002),
    PASSWORD_POLICY_VIOLATION("PASSWORD_POLICY_VIOLATION", "problem.request.title", "problem.account.passwordPolicy", 4002),
    INVITATION_PENDING("INVITATION_PENDING", "problem.conflict.title", "problem.invitation.pending", 5001),
    PROJECT_ALREADY_MEMBER("PROJECT_ALREADY_MEMBER", "problem.conflict.title", "problem.project.alreadyMember", 5001),
    PROJECT_OWNER_CONFLICT("PROJECT_OWNER_CONFLICT", "problem.conflict.title", "problem.project.ownerConflict", 5001),
    QUERY_SYNTAX_INVALID("QUERY_SYNTAX_INVALID", "problem.query.title", "problem.query.syntax", 4007),
    QUERY_READ_ONLY_REQUIRED("QUERY_READ_ONLY_REQUIRED", "problem.query.title", "problem.query.readOnly", 4007),
    QUERY_INPUT_LIMIT_EXCEEDED("QUERY_INPUT_LIMIT_EXCEEDED", "problem.query.title", "problem.query.inputLimit", 4007),
    QUERY_RESULT_LIMIT_EXCEEDED("QUERY_RESULT_LIMIT_EXCEEDED", "problem.query.title", "problem.query.resultLimit", 4007),
    QUERY_BUDGET_EXCEEDED("QUERY_BUDGET_EXCEEDED", "problem.query.title", "problem.query.budget", 4007),
    QUERY_EXECUTION_REJECTED("QUERY_EXECUTION_REJECTED", "problem.query.title", "problem.query.executionRejected", 5004),
    REQUEST_METHOD_NOT_ALLOWED("REQUEST_METHOD_NOT_ALLOWED", "problem.request.title", "problem.request.methodNotAllowed", 4002),
    REQUEST_NOT_ACCEPTABLE("REQUEST_NOT_ACCEPTABLE", "problem.request.title", "problem.request.notAcceptable", 4002),
    REQUEST_TOO_LARGE("REQUEST_TOO_LARGE", "problem.request.title", "problem.request.tooLarge", 4002),
    REQUEST_UNSUPPORTED_MEDIA_TYPE("REQUEST_UNSUPPORTED_MEDIA_TYPE", "problem.request.title", "problem.request.unsupportedMediaType", 4002),
    RATE_LIMITED("RATE_LIMITED", "problem.rateLimit.title", "problem.rateLimit.limited", 1000),
    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "problem.dependency.title", "problem.dependency.unavailable", -1),
    INTERNAL_ERROR("INTERNAL_ERROR", "problem.internal.title", "problem.internal.failed", -1);

    private final String value;
    private final String titleMessageKey;
    private final String detailMessageKey;
    private final int legacyNumericCode;

    ApiErrorCode(final String value, final String titleMessageKey, final String detailMessageKey,
            final int legacyNumericCode) {
        this.value = value;
        this.titleMessageKey = titleMessageKey;
        this.detailMessageKey = detailMessageKey;
        this.legacyNumericCode = legacyNumericCode;
    }
}
