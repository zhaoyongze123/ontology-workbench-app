package com.devin.ontology.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 2025/7/9 14:33.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
public class NodeDTO {

    /**
     * 数据库名称前缀.
     */
    private String dbPrefix;

    /**
     * 数据库id.
     */
    private Long databaseId;

    /**
     * 节点id.
     */
    private Long id;

    /**
     * 节点标签.
     */
    private List<String> labels;

    /**
     * 节点属性.
     */
    private Map<String, Object> properties;
}
