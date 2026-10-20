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

    /**
     * 构造函数.
     *
     * @param message 错误信息
     */
    public ValidateException(final String message) {
        super(message);
        this.errCode = ResultEnum.BUSINESS_ERROR.getCode();
        this.errMsg = message;
    }

    public ValidateException(final Integer errCode, final String message) {
        super(message);
        this.errCode = errCode;
        this.errMsg = message;
    }

    public ValidateException(final ResultEnum resultEnum) {
        super(resultEnum.getMessage());
        this.errCode = resultEnum.getCode();
        this.errMsg = resultEnum.getMessage();
    }
}
