package com.devin.ontology.common.utils.r;

import com.devin.ontology.common.constant.I18nConstant;
import com.devin.ontology.common.utils.i18n.I18nUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 2025/6/13 19:39.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Getter
@AllArgsConstructor
public enum ResultEnum {
    /* 成功状态码 */
    SUCCESS(0, I18nConstant.SUCCESS),
    /* 未知错误 */
    ERROR(-1, I18nConstant.ERROR),
    /* 客户端错误: 1001-1999 */
    CLIENT_ERROR(1000, I18nConstant.CLIENT_ERROR),
    AGE_ERROR(1001, I18nConstant.AGE_ERROR),
    /* 参数错误: 4001-4999 */
    VALIDATE_ERROR(4001, I18nConstant.VALIDATE_ERROR),
    PARAM_IS_INVALID(4002, I18nConstant.PARAM_IS_INVALID),
    API_KEY_ILLEGAL(4003, I18nConstant.API_KEY_ILLEGAL),
    CYPHER_IS_INVALID(4007, I18nConstant.CYPHER_IS_INVALID),
    /* 业务错误: 5001-5999 */
    BUSINESS_ERROR(5001, I18nConstant.BUSINESS_ERROR),
    DUPLICATE_KEY(5002, I18nConstant.DUPLICATE_KEY),
    ORGANIZATION_ERROR(5003, I18nConstant.ORGANIZATION_ERROR),
    GRAPH_ERROR(5004, I18nConstant.GRAPH_ERROR),
    NODE_ERROR(5005, I18nConstant.NODE_ERROR),
    RELATIONSHIP_ERROR(5006, I18nConstant.RELATIONSHIP_ERROR);

    private final Integer code;

    private final String messageKey;

    /**
     * 获取国际化消息.
     * @return 消息内容
     */
    public String getMessage() {
        return I18nUtils.getMessage(messageKey);
    }
}
