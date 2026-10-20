package com.devin.ontology.common.enums;

import com.devin.ontology.common.exception.ValidateException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Arrays;

/**
 * 2025/6/30 14:54.
 *
 * <p>
 *     本体实体类型枚举
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Getter
@AllArgsConstructor
public enum GraphTypeEnum {

    /**
     * 本体类型.
     */
    ONTOLOGY(0, "ontologyGraphStrategy"),

    /**
     * 实体类型.
     */
    ENTITY(1, "entityGraphStrategy");

    private final Integer type;

    private final String strategy;

    /**
     * 根据类型获取枚举.
     * @param graphTypeEnum  类型
     * @return 枚举
     */
    public static Integer of(final GraphTypeEnum graphTypeEnum) {
        return Arrays.stream(values())
                .filter(item -> item.equals(graphTypeEnum))
                .findFirst()
                .map(GraphTypeEnum::getType)
                .orElseThrow(() -> new ValidateException("无效的图谱类型"));
    }

    /**
     * 获取图数据库前缀.
     * @return 前缀
     */
    public String getDbPrefix() {
        return this.name().toLowerCase();
    }
}
