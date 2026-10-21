package com.devin.ontology.strategy.graph;

import com.alibaba.fastjson2.JSON;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.common.utils.BeanCopyUtils;
import com.devin.ontology.dao.EntityDao;
import com.devin.ontology.dao.EntityRelationshipDao;
import com.devin.ontology.dao.GraphDao;
import com.devin.ontology.dao.OntologyDao;
import com.devin.ontology.dao.OntologyRelationshipDao;
import com.devin.ontology.dao.OrganizationDao;
import com.devin.ontology.domain.dto.GraphDTO;
import com.devin.ontology.domain.dto.NodeDTO;
import com.devin.ontology.domain.dto.RelationshipDTO;
import com.devin.ontology.domain.entity.Entity;
import com.devin.ontology.domain.entity.EntityRelationship;
import com.devin.ontology.domain.entity.Graph;
import com.devin.ontology.domain.entity.Ontology;
import com.devin.ontology.domain.entity.OntologyRelationship;
import com.devin.ontology.domain.vo.GraphSaveVO;
import com.devin.ontology.domain.vo.GraphUpdateVO;
import com.devin.ontology.domain.vo.GraphVO;
import com.devin.ontology.domain.vo.NodeDeleteVO;
import com.devin.ontology.domain.vo.NodeSaveVO;
import com.devin.ontology.domain.vo.NodeUpdateVO;
import com.devin.ontology.domain.vo.NodeVO;
import com.devin.ontology.domain.vo.RelationshipDeleteVO;
import com.devin.ontology.domain.vo.RelationshipSaveVO;
import com.devin.ontology.domain.vo.RelationshipUpdateVO;
import com.devin.ontology.domain.vo.RelationshipVO;
import com.devin.ontology.model.PropertySchema;
import com.devin.ontology.model.JsonGraphInfo;
import com.devin.ontology.strategy.storage.ChartStorageStrategy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 2025/11/16 02:20.
 *
 * <p>
 * 图谱策略抽象类
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@Component
@AllArgsConstructor
public abstract class GraphStrategyAbstract implements GraphStrategy {

    private final ChartStorageStrategy chartStorageStrategy;

    private final GraphDao graphDao;

    private final OrganizationDao organizationDao;

    private final OntologyDao ontologyDao;

    private final EntityDao entityDao;

    private final OntologyRelationshipDao ontologyRelationshipDao;

    private final EntityRelationshipDao entityRelationshipDao;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public GraphVO saveGraph(final GraphSaveVO graphSaveVO) {
        Graph graph = BeanCopyUtils.copy(graphSaveVO, Graph.class);
        graph.setType(graphSaveVO.getGraphType().name());
        if (GraphTypeEnum.ENTITY.equals(graphSaveVO.getGraphType())) {
            // 检查本体图谱是否在同一组织下
            BigInteger organizationId = graphDao.getById(graph.getLinkGraphId()).getOrganizationId();
            if (!organizationId.equals(graphSaveVO.getOrganizationId())) {
                throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
            }
        }
        // 查询图谱是否已经存在
        Long graphCount = graphDao.lambdaQuery()
                .eq(Graph::getOrganizationId, graphSaveVO.getOrganizationId())
                .eq(Graph::getType, graph.getType())
                .eq(Graph::getName, graph.getName())
                .count();
        if (graphCount > 0) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_NAME_CONFLICT);
        }

        GraphVO graphVO = BeanCopyUtils.copy(graph.save(), GraphVO.class);
        // 创建图谱
        chartStorageStrategy.createDatabase(getGraphType().getDbPrefix(), graphVO.getId().longValue());
        // 更新组织项目时间
        organizationDao.updateTime(graphVO.getOrganizationId());
        return graphVO;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void updateGraph(final GraphUpdateVO graphUpdateVO) {
        // 查询图谱
        Graph originalGraph = graphDao.getById(graphUpdateVO.getId());
        if (Objects.isNull(originalGraph)) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_NOT_FOUND);
        }
        // 查询图谱是否已经存在
        Long graphCount = graphDao.lambdaQuery()
                .eq(Graph::getOrganizationId, graphUpdateVO.getOrganizationId())
                .eq(Graph::getType, getGraphType())
                .and(
                        queryWrapper -> {
                            queryWrapper.eq(Graph::getName, graphUpdateVO.getName())
                                    .ne(Graph::getName, originalGraph.getName());
                        }
                )
                .count();
        if (graphCount > 0) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_NAME_CONFLICT);
        }
        Graph graph = BeanCopyUtils.copy(graphUpdateVO, Graph.class);
        graph.update();
        // 更新组织项目时间
        organizationDao.updateTime(graphUpdateVO.getOrganizationId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void saveNode(final NodeSaveVO nodeSaveVO) {
        // 检查节点是否存在
        checkNodeExist(BeanCopyUtils.copy(nodeSaveVO, NodeVO.class));
        // 检查节点是否合法
        checkNodeValid(nodeSaveVO.getName(), nodeSaveVO.getPropertySchemaJson());
        // 保存节点
        NodeDTO nodeDTO = doSaveNode(nodeSaveVO);
        // 保存到图数据库
        chartStorageStrategy.createNode(nodeDTO);
        // 更新图谱时间
        graphDao.updateTime(nodeSaveVO.getGraphId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void updateNode(final NodeUpdateVO nodeUpdateVO) {
        // 检查节点是否重复
        checkNodeExist(BeanCopyUtils.copy(nodeUpdateVO, NodeVO.class));
        // 检查节点是否合法
        checkNodeValid(nodeUpdateVO.getName(), nodeUpdateVO.getPropertySchemaJson());
        // 更新节点
        NodeDTO nodeDTO = doUpdateNode(nodeUpdateVO);
        // 更新图数据库
        chartStorageStrategy.editNode(nodeDTO);
        // 更新图谱时间
        graphDao.updateTime(nodeUpdateVO.getGraphId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteNode(final NodeDeleteVO nodeDeleteVO) {
        if (nodeDeleteVO.getIdList().isEmpty()) {
            return;
        }
        // 检查节点是否含有关系
        if (
                getRelationshipCount(
                        nodeDeleteVO.getIdList()
                                .stream()
                                .toList(),
                        nodeDeleteVO.getGraphId()
                ) > 0L
        ) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_NODE_HAS_DEPENDENCIES);
        }
        // 删除节点
        doDeleteNode(nodeDeleteVO);
        // 删除图数据库
        chartStorageStrategy.deleteNodeBatch(
                nodeDeleteVO.getIdList().stream().map(BigInteger::longValue).toList(),
                nodeDeleteVO.getGraphId().longValue(),
                getGraphType().getDbPrefix()
        );
        // 更新图谱时间
        graphDao.updateTime(nodeDeleteVO.getGraphId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void saveRelationship(final RelationshipSaveVO relationshipSaveVO) {
        // 检查关系是否存在
        checkRelationshipExist(BeanCopyUtils.copy(relationshipSaveVO, RelationshipVO.class));
        // 检查节点是否合法
        checkNodeValid(relationshipSaveVO.getName(), relationshipSaveVO.getPropertySchemaJson());
        // 保存关系
        RelationshipDTO relationshipDTO = doSaveRelationship(relationshipSaveVO);
        // 保存到图数据库
        chartStorageStrategy.createRelationship(relationshipDTO);
        // 更新图谱时间
        graphDao.updateTime(relationshipSaveVO.getGraphId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void updateRelationship(final RelationshipUpdateVO relationshipUpdateVO) {
        // 删除关系
        RelationshipDeleteVO relationshipDeleteVO = new RelationshipDeleteVO();
        relationshipDeleteVO.setIdList(List.of(relationshipUpdateVO.getId()));
        doDeleteRelationship(relationshipDeleteVO);
        // 检查关系是否存在
        checkRelationshipExist(BeanCopyUtils.copy(relationshipUpdateVO, RelationshipVO.class));
        // 检查节点是否合法
        checkNodeValid(relationshipUpdateVO.getName(), relationshipUpdateVO.getPropertySchemaJson());
        // 更新关系
        RelationshipDTO relationshipDTO = doUpdateRelationship(relationshipUpdateVO);
        // 更新图数据库
        chartStorageStrategy.editRelationship(relationshipDTO);
        // 更新图谱时间
        graphDao.updateTime(relationshipUpdateVO.getGraphId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteRelationship(final RelationshipDeleteVO relationshipDelete) {
        if (relationshipDelete.getIdList().isEmpty()) {
            return;
        }
        doDeleteRelationship(relationshipDelete);
        chartStorageStrategy.deleteRelationshipBatch(
                relationshipDelete.getIdList().stream().map(BigInteger::longValue).toList(),
                relationshipDelete.getGraphId().longValue(),
                getGraphType().getDbPrefix()
        );
        // 更新图谱时间
        organizationDao.updateTime(relationshipDelete.getGraphId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteGraph(final GraphVO graphVO) {
        // 获取图谱下的信息
        Long count = getNodeCount(graphVO.getId());
        if (count > 0) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_HAS_DEPENDENCIES);
        }
        // 删除图谱
        BeanCopyUtils.copy(graphVO, Graph.class)
                .remove();
        // 删除图谱数据库
        chartStorageStrategy.dropDatabase(
                Collections.singletonList(graphVO.getId().longValue()),
                getGraphType().getDbPrefix()
        );
        // 更新组织时间
        organizationDao.updateTime(graphVO.getOrganizationId());
    }

    @Override
    public GraphVO getGraphInfo(final BigInteger graphId) {
        Graph graph = getGraphDao().getById(graphId);
        return BeanCopyUtils.copy(graph, GraphVO.class);
    }

    @Override
    public JsonGraphInfo exportGraph(final BigInteger graphId) {
        Graph graph = graphDao.lambdaQuery()
                .eq(Graph::getId, graphId)
                .eq(Graph::getType, getGraphType().name())
                .one();
        if (Objects.isNull(graph)) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_NOT_FOUND);
        }
        JsonGraphInfo jsonGraphInfo = new JsonGraphInfo();
        jsonGraphInfo.setGraph(graph);
        return doExportGraph(jsonGraphInfo);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void importGraph(
            final String jsonData,
            final BigInteger organizationId
    ) {
        JsonGraphInfo jsonGraphInfo = new JsonGraphInfo();
        try {
            jsonGraphInfo = JSON.parseObject(jsonData, JsonGraphInfo.class);
        } catch (Exception e) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_IMPORT_INVALID);
        }
        // 检查本体节点和本体关系是否有重名存在
        checkDuplicateNodeAndRelationship(jsonGraphInfo);
        Graph graph = jsonGraphInfo.getGraph();
        if (Objects.isNull(graph)) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_IMPORT_INVALID);
        }
        // 查询是否符合当前上传类型
        if (!getGraphType().name().equals(graph.getType())) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_TYPE_MISMATCH);
        }
        doImportGraph(jsonGraphInfo, organizationId);
    }

    /**
     * 检查本体节点和本体关系是否有重名存在.
     *
     * @param jsonGraphInfo jsonGraphInfo
     */
    private void checkDuplicateNodeAndRelationship(final JsonGraphInfo jsonGraphInfo) {
        List<Ontology> ontologyList = jsonGraphInfo.getOntologyList();
        List<OntologyRelationship> ontologyRelationshipList = jsonGraphInfo.getOntologyRelationshipList();
        if (
                CollectionUtils.isEmpty(ontologyList)
                        || CollectionUtils.isEmpty(ontologyRelationshipList)
        ) {
            return;
        }
        Set<String> nodeNameSet = ontologyList.stream().map(Ontology::getName).collect(Collectors.toSet());
        Set<String> relationshipNameSet = ontologyRelationshipList.stream().map(OntologyRelationship::getName).collect(Collectors.toSet());
        // 判断是否重名
        Set<String> duplicateName = nodeNameSet.stream()
                .filter(relationshipNameSet::contains)
                .collect(Collectors.toSet());
        if (!duplicateName.isEmpty()) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_IMPORT_INVALID);
        }
    }

    /**
     * 检查节点是否合法.
     *
     * @param nodeName   节点名称
     * @param schemaList 属性信息列表
     */
    private void checkNodeValid(final String nodeName, final List<PropertySchema> schemaList) {
        // 检查节点名称是否合法
        if (!nodeName.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        if (schemaList.isEmpty()) {
            return;
        }
        // 1. 判断属性name是否为空
        long emptyNameCount = schemaList.stream()
                .filter(schema -> StringUtils.isBlank(schema.getName()))
                .count();
        if (emptyNameCount > 0) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        // 2. 检查属性名称是否重复
        long propertyUniqueCount = schemaList.stream()
                .map(PropertySchema::getName)
                .map(String::trim)
                .distinct()
                .count();
        if (propertyUniqueCount != schemaList.size()) {
            throw new OntologyApiException(ApiErrorCode.RESOURCE_CONFLICT);
        }
    }

    /**
     * 获取节点统计.
     *
     * @param graphId 图谱id
     * @return count
     */
    protected Long getNodeCount(
            final BigInteger graphId
    ) {
        return switch (getGraphType().name()) {
            case "ONTOLOGY" -> {
                yield ontologyDao.lambdaQuery()
                        .eq(Ontology::getGraphId, graphId)
                        .count();
            }
            case "ENTITY" -> {
                yield entityDao.lambdaQuery()
                        .eq(Entity::getGraphId, graphId)
                        .count();
            }
            default -> throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        };
    }

    /**
     * 获取关系统计.
     *
     * @param nodeIdList 节点id列表
     * @param graphId    图谱id
     * @return count
     */
    protected Long getRelationshipCount(
            final List<BigInteger> nodeIdList,
            final BigInteger graphId
    ) {
        if (nodeIdList.isEmpty()) {
            return 0L;
        }
        return switch (getGraphType().name()) {
            case "ONTOLOGY" -> {
                yield ontologyRelationshipDao.lambdaQuery()
                        .eq(OntologyRelationship::getGraphId, graphId)
                        .and(wrapper ->
                                wrapper.in(OntologyRelationship::getSourceNodeId, nodeIdList)
                                        .or()
                                        .in(OntologyRelationship::getTargetNodeId, nodeIdList)
                        )
                        .count();
            }
            case "ENTITY" -> {
                yield entityRelationshipDao.lambdaQuery()
                        .eq(EntityRelationship::getGraphId, graphId)
                        .and(wrapper ->
                                wrapper.in(EntityRelationship::getSourceNodeId, nodeIdList)
                                        .or()
                                        .in(EntityRelationship::getTargetNodeId, nodeIdList)
                        )
                        .count();
            }
            default -> throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        };
    }

    /**
     * 属性转化.
     *
     * @param schemaList 属性信息列表
     * @return 属性信息
     */
    protected Map<String, Object> convertProperty(final List<PropertySchema> schemaList) {
        return schemaList
                .stream()
                .collect(
                        Collectors.toMap(PropertySchema::getName,
                                schema -> Optional.ofNullable(schema.getDescription()).orElse(""),
                                // 去除重复项
                                (existing, replacement) -> existing)
                );
    }

    /**
     * 检查节点是否存在.
     *
     * @param nodeVO 节点信息
     */
    public abstract void checkNodeExist(NodeVO nodeVO);

    /**
     * 保存节点.
     *
     * @param nodeSaveVO 节点保存信息
     * @return NodeDTO
     */
    public abstract NodeDTO doSaveNode(NodeSaveVO nodeSaveVO);

    /**
     * 更新节点.
     *
     * @param nodeUpdateVO 节点更新信息
     * @return NodeDTO
     */
    public abstract NodeDTO doUpdateNode(NodeUpdateVO nodeUpdateVO);

    /**
     * 删除节点.
     *
     * @param nodeDeleteVO 节点删除信息
     */
    public abstract void doDeleteNode(NodeDeleteVO nodeDeleteVO);

    /**
     * 检查关系是否存在.
     *
     * @param relationshipVO 关系信息
     */
    public abstract void checkRelationshipExist(RelationshipVO relationshipVO);

    /**
     * 保存关系.
     *
     * @param relationshipSaveVO 关系保存信息
     * @return 保存后的关系信息
     */
    public abstract RelationshipDTO doSaveRelationship(RelationshipSaveVO relationshipSaveVO);

    /**
     * 更新关系.
     *
     * @param relationshipUpdateVO 关系修改信息
     * @return RelationshipDTO
     */
    public abstract RelationshipDTO doUpdateRelationship(RelationshipUpdateVO relationshipUpdateVO);

    /**
     * 删除关系.
     *
     * @param relationshipDelete 删除关系参数
     */
    public abstract void doDeleteRelationship(RelationshipDeleteVO relationshipDelete);

    /**
     * 导出图谱.
     *
     * @param jsonGraphInfo 图谱信息
     * @return jsonGraphInfo
     */
    public abstract JsonGraphInfo doExportGraph(JsonGraphInfo jsonGraphInfo);

    /**
     * 导入图谱.
     *
     * @param jsonGraphInfo  图谱信息
     * @param organizationId 组织id
     * @return GraphDTO
     */
    public abstract GraphDTO doImportGraph(JsonGraphInfo jsonGraphInfo, BigInteger organizationId);
}
