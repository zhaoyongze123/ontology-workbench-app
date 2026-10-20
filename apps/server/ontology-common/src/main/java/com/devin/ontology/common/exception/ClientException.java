package com.devin.ontology.common.exception;

import com.devin.ontology.common.utils.r.ResultEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 2025/12/10 21:19.
 *
 * <p>
 *     客户端异常
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ClientException extends RuntimeException {

    private final Integer errCode;

    private final String errMsg;

    public ClientException(final Integer errCode, final String errMsg) {
        super(errMsg);
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    public ClientException(final String message) {
        super(message);
        this.errCode = ResultEnum.CLIENT_ERROR.getCode();
        this.errMsg = message;
    }

    public ClientException(final ResultEnum resultEnum) {
        super(resultEnum.getMessage());
        this.errCode = resultEnum.getCode();
        this.errMsg = resultEnum.getMessage();
    }

    public ClientException(final ResultEnum resultEnum, final String message) {
        super(resultEnum.getMessage());
        this.errCode = resultEnum.getCode();
        this.errMsg = message;
    }
}
