package com.devin.ontology.common.exception;

/** 由业务层抛出的可预期错误，只携带稳定错误原因，不决定 HTTP 状态。 */
public class OntologyApiException extends RuntimeException {
    private final ApiErrorCode errorCode;

    public OntologyApiException(final ApiErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public OntologyApiException(final ApiErrorCode errorCode, final Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }
}
