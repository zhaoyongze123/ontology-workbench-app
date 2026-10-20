package com.devin.ontology.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 2025/6/9 11:37.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Getter
@AllArgsConstructor
public enum ChartTypeEnum {

    NEO4J("neo4j", "neo4jStrategy"),
    APACHE_AGE("apacheAge", "apacheAgeStrategy");

    private static final Map<String, ChartTypeEnum> CACHE;

    /**
     * 存储类型.
     */
    private final String type;

    /**
     * 存储策略.
     */
    private final String strategy;

    static {
        CACHE = Arrays.stream(ChartTypeEnum.values()).collect(Collectors.toMap(ChartTypeEnum::getType, Function.identity()));
    }

    /**
     * 根据类型获取枚举.
     * @param type 类型
     * @return 枚举
     */
    public static ChartTypeEnum of(final String type) {
        return CACHE.get(type);
    }
}
