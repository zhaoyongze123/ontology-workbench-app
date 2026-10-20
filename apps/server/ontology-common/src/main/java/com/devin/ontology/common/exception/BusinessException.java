package com.devin.ontology.common.exception;

import com.devin.ontology.common.utils.r.ResultEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 2025/6/12 16:17.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {

    private final Integer errCode;

    private final String errMsg;

    /**
     * 构造函数.
     *
     * @param message 错误信息
     */
    public BusinessException(final String message) {
        super(message);
        this.errCode = ResultEnum.BUSINESS_ERROR.getCode();
        this.errMsg = message;
    }

    public BusinessException(final Integer errCode, final String message) {
        super(message);
        this.errCode = errCode;
        this.errMsg = message;
    }

    public BusinessException(final ResultEnum resultEnum) {
        super(resultEnum.getMessage());
        this.errCode = resultEnum.getCode();
        this.errMsg = resultEnum.getMessage();
    }

    public BusinessException(final ResultEnum resultEnum, final String message) {
        super(resultEnum.getMessage());
        this.errCode = resultEnum.getCode();
        this.errMsg = message;
    }
}
