package com.devin.ontology.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 2025/7/9 14:35.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
public class RelationshipDTO {

    /**
     * 数据库名前缀.
     */
    private String dbPrefix;

    /**
     * 数据库id.
     */
    private Long databaseId;

    /**
     * 关系id.
     */
    private Long id;

    /**
     * 关系名称.
     */
    private String name;

    /**
     * 源节点id.
     */
    private Long sourceId;

    /**
     * 目标节点id.
     */
    private Long targetId;

    /**
     * 关系属性.
     */
    private Map<String, Object> properties;
}
