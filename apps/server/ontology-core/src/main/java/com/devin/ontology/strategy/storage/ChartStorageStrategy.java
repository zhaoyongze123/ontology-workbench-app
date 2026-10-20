package com.devin.ontology.strategy.storage;

import com.devin.ontology.domain.dto.GraphDTO;
import com.devin.ontology.domain.dto.NodeDTO;
import com.devin.ontology.domain.dto.RelationshipDTO;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 2025/6/5 10:17.
 *
 * <p>
 * 图存储类型策略
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
public interface ChartStorageStrategy {

    /**
     * 创建数据库.
     * @param dbPrefix 数据库前缀
     * @param id 数据库id
     */
    void createDatabase(String dbPrefix, Long id);

    /**
     * 删除数据库.
     * @param ids 数据库id
     * @param dbPrefix 数据库前缀
     */
    void dropDatabase(Collection<Long> ids, String dbPrefix);

    /**
     * 保存图谱节点信息.
     *
     * @param nodeDTO 图谱节点信息
     */
    void createNode(NodeDTO nodeDTO);

    /**
     * 批量保存图谱节点信息.
     *
     * @param nodeDTOList 节点信息列表
     */
    void createNodeBatch(List<NodeDTO> nodeDTOList);

    /**
     * 创建关系.
     *
     * @param relationshipDTO 关系信息
     */
    void createRelationship(RelationshipDTO relationshipDTO);

    /**
     * 删除图谱节点.
     *
     * @param ids 图谱节点ID
     * @param databaseId 图谱ID
     * @param dbPrefix 数据库前缀
     */
    void deleteNodeBatch(List<Long> ids, Long databaseId, String dbPrefix);

    /**
     * 修改图谱节点信息.
     *
     * @param nodeDTO 图谱节点信息
     */
    void editNode(NodeDTO nodeDTO);

    /**
     * 通过ID查询图谱节点信息.
     *
     * @param id 图谱节点ID
     * @param databaseId 图谱ID
     * @param dbPrefix 数据库前缀
     * @return 图谱节点信息
     */
    Optional<Map<String, Object>> findNodeById(Long id, Long databaseId, String dbPrefix);

    /**
     * 删除图谱关系.
     *
     * @param ids 图谱关系ID
     * @param databaseId 图谱ID
     * @param dbPrefix 数据库前缀
     */
    void deleteRelationshipBatch(List<Long> ids, Long databaseId, String dbPrefix);

    /**
     * 修改图谱关系信息.
     *
     * @param relationshipDTO 图谱关系信息
     */
    void editRelationship(RelationshipDTO relationshipDTO);

    /**
     * 通过ID查询图谱关系信息.
     *
     * @param id 图谱关系ID
     * @param databaseId 图谱ID
     * @param dbPrefix 图谱关系数据库前缀
     * @return 图谱关系信息
     */
    Optional<Map<String, Object>> findRelationshipById(Long id, Long databaseId, String dbPrefix);

    /**
     * 查询.
     * @param cypher cypher
     * @param databaseId 图谱ID
     * @param dbPrefix 图谱关系数据库前缀
     * @return 结果
     */
    Collection<Map<String, Object>> query(String cypher, Long databaseId, String dbPrefix);

    /**
     * 批量更新节点.
     * @param nodeDTOList 节点信息
     */
    void editNodeBatch(List<NodeDTO> nodeDTOList);

    /**
     * 批量导入节点和关系.
     * @param graphDTO 图谱信息
     */
    void importNodeAndRelationshipBatch(GraphDTO graphDTO);
}
