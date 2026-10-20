package com.devin.ontology.domain.dto;

import lombok.Data;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 2025/11/22 18:49.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class GraphDTO {

    /**
     * 数据库前缀.
     */
    private String dbPrefix;

    /**
     * 数据库id.
     */
    private Long databaseId;

    /**
     * 节点列表.
     */
    private List<NodeDTO> nodeDTOList;

    /**
     * 关系列表.
     */
    private List<RelationshipDTO> relationshipDTOList;

    /**
     * 节点关系映射.
     */
    private Map<BigInteger, Map<String, String>> ontologyIdMap;

    /**
     * 实体id映射.
     */
    private Map<BigInteger, BigInteger> entityIdMap;

    /**
     * 实体关系映射.
     */
    private Map<BigInteger, Map<String, String>> ontologyRelationshipIdMap;
}
