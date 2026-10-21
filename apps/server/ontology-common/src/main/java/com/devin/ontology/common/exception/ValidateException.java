package com.devin.ontology.common.exception;

import com.devin.ontology.common.utils.r.ResultEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 2025/7/2 10:29.
 *
 * <p>
 * 信息校验异常
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ValidateException extends RuntimeException {

    private final Integer errCode;

    private final String errMsg;

    private final ApiErrorCode apiErrorCode;

    /**
     * 构造函数.
     *
     * @param message 错误信息
     */
    public ValidateException(final String message) {
        super(message);
        this.errCode = ResultEnum.BUSINESS_ERROR.getCode();
        this.errMsg = message;
        this.apiErrorCode = null;
    }

    public ValidateException(final Integer errCode, final String message) {
        super(message);
        this.errCode = errCode;
        this.errMsg = message;
        this.apiErrorCode = null;
    }

    public ValidateException(final ResultEnum resultEnum) {
        super(resultEnum.getMessage());
        this.errCode = resultEnum.getCode();
        this.errMsg = resultEnum.getMessage();
        this.apiErrorCode = null;
    }

    /** 使用稳定错误目录表达原因，同时保留旧数字码供兼容序列化。 */
    public ValidateException(final ApiErrorCode apiErrorCode) {
        super(apiErrorCode.name());
        this.errCode = apiErrorCode.getLegacyNumericCode();
        this.errMsg = apiErrorCode.name();
        this.apiErrorCode = apiErrorCode;
    }

    public ValidateException(final ApiErrorCode apiErrorCode, final Throwable cause) {
        super(apiErrorCode.name(), cause);
        this.errCode = apiErrorCode.getLegacyNumericCode();
        this.errMsg = apiErrorCode.name();
        this.apiErrorCode = apiErrorCode;
    }
}
