package com.devin.ontology.common.enums;

import com.devin.ontology.common.exception.ValidateException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 2025/6/30 0:35.
 *
 * <p>
 * 属性类型枚举
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Getter
@AllArgsConstructor
public enum PropertyTypeEnum {

    /**
     * 字符串.
     */
    STRING(1),

    /**
     * 数字.
     */
    NUMBER(2),

    /**
     * 布尔.
     */
    BOOLEAN(3),

    /**
     * 对象.
     */
    OBJECT(4);

    private static final Map<Integer, PropertyTypeEnum> CACHE;

    private final Integer type;

    static {
        CACHE = Arrays.stream(values())
                .collect(Collectors.toMap(PropertyTypeEnum::getType, Function.identity()));
    }

    /**
     * 根据类型获取枚举.
     *
     * @param propertyTypeEnum 枚举值
     * @return 枚举
     */
    public static Integer of(final PropertyTypeEnum propertyTypeEnum) {
        return Arrays.stream(values())
                .filter(e -> e.equals(propertyTypeEnum))
                .findFirst()
                .map(PropertyTypeEnum::getType)
                .orElseThrow(() -> new ValidateException("没有对应的属性类型，请联系管理员进行操作！！！"));
    }

    /**
     * 根据类型获取枚举.
     *
     * @param type 枚举值
     * @return 枚举
     */
    public static PropertyTypeEnum of(final Integer type) {
        return CACHE.get(type);
    }
}
