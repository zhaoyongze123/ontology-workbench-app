package com.devin.ontology.strategy.graph;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.utils.r.PageResult;
import com.devin.ontology.domain.vo.GraphQueryVO;
import com.devin.ontology.domain.vo.GraphSaveVO;
import com.devin.ontology.domain.vo.GraphUpdateVO;
import com.devin.ontology.domain.vo.GraphVO;
import com.devin.ontology.domain.vo.NodeDeleteVO;
import com.devin.ontology.domain.vo.NodeQueryVO;
import com.devin.ontology.domain.vo.NodeSaveVO;
import com.devin.ontology.domain.vo.NodeUpdateVO;
import com.devin.ontology.domain.vo.NodeVO;
import com.devin.ontology.domain.vo.OptionalVO;
import com.devin.ontology.domain.vo.RelationshipDeleteVO;
import com.devin.ontology.domain.vo.RelationshipQueryVO;
import com.devin.ontology.domain.vo.RelationshipSaveVO;
import com.devin.ontology.domain.vo.RelationshipUpdateVO;
import com.devin.ontology.domain.vo.RelationshipVO;
import com.devin.ontology.model.JsonGraphInfo;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/16 02:20.
 *
 * <p>
 * 图谱策略
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
public interface GraphStrategy {

    /**
     * 获取图谱类型.
     *
     * @return 图谱类型枚举
     */
    GraphTypeEnum getGraphType();

    /**
     * 保存图谱.
     *
     * @param graphSaveVO 图谱保存VO
     * @return 图谱VO
     */
    GraphVO saveGraph(GraphSaveVO graphSaveVO);

    /**
     * 修改图谱.
     *
     * @param graphUpdateVO 图谱修改VO
     */
    void updateGraph(GraphUpdateVO graphUpdateVO);

    /**
     * 保存节点对象.
     *
     * @param nodeSaveVO 节点对象保存信息
     */
    void saveNode(NodeSaveVO nodeSaveVO);

    /**
     * 修改节点对象.
     *
     * @param nodeUpdateVO 节点对象修改信息
     */
    void updateNode(NodeUpdateVO nodeUpdateVO);

    /**
     * 删除节点对象.
     *
     * @param nodeDeleteVO 节点对象删除信息
     */
    void deleteNode(NodeDeleteVO nodeDeleteVO);

    /**
     * 获取节点对象列表.
     *
     * @param queryVO 节点对象查询信息
     * @return 节点对象列表
     */
    PageResult<NodeVO> pageNode(NodeQueryVO queryVO);

    /**
     * 保存关系对象.
     *
     * @param relationshipSaveVO 关系对象保存信息
     */
    void saveRelationship(RelationshipSaveVO relationshipSaveVO);

    /**
     * 修改关系对象.
     *
     * @param relationshipUpdateVO 关系对象修改信息
     */
    void updateRelationship(RelationshipUpdateVO relationshipUpdateVO);

    /**
     * 分页查询关系对象.
     *
     * @param queryVO 查询条件
     * @return PageResult
     */
    PageResult<RelationshipVO> pageRelationship(RelationshipQueryVO queryVO);

    /**
     * 删除对象关系.
     *
     * @param relationshipDelete 删除信息
     */
    void deleteRelationship(RelationshipDeleteVO relationshipDelete);

    /**
     * 分页查询图谱.
     *
     * @param queryVO 查询条件
     * @return PageResult
     */
    PageResult<GraphVO> pageGraph(GraphQueryVO queryVO);

    /**
     * 删除图谱.
     *
     * @param graphVO 删除信息
     */
    void deleteGraph(GraphVO graphVO);

    /**
     * 获取图谱信息.
     *
     * @param graphId 图谱ID
     * @return GraphVO
     */
    GraphVO getGraphInfo(BigInteger graphId);

    /**
     * 获取节点信息.
     *
     * @param nodeId 节点ID
     * @return NodeVO
     */
    NodeVO getNodeInfo(BigInteger nodeId);

    /**
     * 获取关系信息.
     *
     * @param relationshipId 关系ID
     * @return RelationshipVO
     */
    RelationshipVO getRelationshipInfo(BigInteger relationshipId);

    /**
     * 导出图谱.
     *
     * @param graphId 图谱ID
     * @return JsonGraphInfo
     */
    JsonGraphInfo exportGraph(BigInteger graphId);

    /**
     * 获取图谱可选节点.
     *
     * @param graphId 图谱id
     * @return List
     */
    List<OptionalVO> getNodeOptional(BigInteger graphId);

    /**
     * 导入图谱.
     *
     * @param jsonData       文件Json数据
     * @param organizationId 项目id
     */
    void importGraph(String jsonData, BigInteger organizationId);

    /**
     * 获取图谱可选关系.
     *
     * @param graphId 图谱id
     * @return List
     */
    List<OptionalVO> getRelationshipOptional(BigInteger graphId);
}
