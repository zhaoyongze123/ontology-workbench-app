package com.devin.ontology.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.postgresql.util.PGobject;
import java.util.Collection;
import java.util.Map;

/**
 * 2025/9/29 16:26.
 *
 * <p>
 * 值类型枚举
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum ValueType {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    MAP("map"),
    COLLECTION("collection"),
    ARRAY("array"),
    PG_OBJECT("pgObject"),
    ITERABLE("iterable"),
    OTHER("other");

    private final String type;

    /**
     * 获取值类型.
     * @param value 值
     * @return 值类型
     */
    // CHECKSTYLE:OFF
    public static ValueType of(final Object value) {
        if (value instanceof String) {
            return ValueType.STRING;
        }
        if (value instanceof Number) {
            return ValueType.NUMBER;
        }
        if (value instanceof Boolean) {
            return ValueType.BOOLEAN;
        }
        if (value instanceof Map<?, ?>) {
            return ValueType.MAP;
        }
        if (value instanceof Collection) {
            return ValueType.COLLECTION;
        }
        if (value.getClass().isArray()) {
            return ValueType.ARRAY;
        }
        if (value instanceof PGobject) {
            return ValueType.PG_OBJECT;
        }
        if (value instanceof Iterable<?>) {
            return ValueType.ITERABLE;
        }
        return ValueType.OTHER;
    }
    // CHECKSTYLE:ON
}
