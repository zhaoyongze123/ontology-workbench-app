package com.devin.ontology.strategy.graph.impl;

import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devin.ontology.common.constant.MapKeyConstant;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.common.utils.BeanCopyUtils;
import com.devin.ontology.common.utils.IdGenerator;
import com.devin.ontology.common.utils.r.PageResult;
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
import com.devin.ontology.domain.vo.GraphQueryVO;
import com.devin.ontology.domain.vo.GraphSaveVO;
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
import com.devin.ontology.model.PropertySchema;
import com.devin.ontology.model.JsonGraphInfo;
import com.devin.ontology.model.validate.JsonSchemaValidator;
import com.devin.ontology.strategy.graph.GraphStrategyAbstract;
import com.devin.ontology.strategy.storage.ChartStorageStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 2025/11/16 02:25.
 *
 * <p>
 * 实体图谱策略
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
public class EntityGraphStrategy extends GraphStrategyAbstract {

    private final OntologyGraphStrategy ontologyGraphStrategy;

    public EntityGraphStrategy(
            final ChartStorageStrategy chartStorageStrategy,
            final GraphDao graphDao,
            final OrganizationDao organizationDao,
            final OntologyDao ontologyDao,
            final EntityDao entityDao,
            final OntologyRelationshipDao ontologyRelationshipDao,
            final EntityRelationshipDao entityRelationshipDao,
            final OntologyGraphStrategy ontologyGraphStrategy
    ) {
        super(chartStorageStrategy, graphDao, organizationDao, ontologyDao, entityDao, ontologyRelationshipDao, entityRelationshipDao);
        this.ontologyGraphStrategy = ontologyGraphStrategy;
    }

    @Override
    public GraphTypeEnum getGraphType() {
        return GraphTypeEnum.ENTITY;
    }

    @Override
    public PageResult<NodeVO> pageNode(final NodeQueryVO queryVO) {
        Page<Entity> page = getEntityDao().pageByQuery(queryVO);
        if (page.getTotal() == 0) {
            return PageResult.ofEmpty(page);
        }
        // 查询本体信息
        List<Entity> entityList = page.getRecords();
        Set<BigInteger> ontologyIdSet = entityList.stream().map(Entity::getOntologyId).collect(Collectors.toSet());
        Map<BigInteger, Ontology> ontologyMap = getOntologyDao()
                .listByIds(ontologyIdSet)
                .stream()
                .collect(
                        Collectors.toMap(Ontology::getId, ontology -> ontology)
                );
        List<NodeVO> nodeVOList = page.getRecords().stream()
                .map(entity -> {
                    NodeVO nodeVO = BeanCopyUtils.copy(entity, NodeVO.class);
                    List<PropertySchema> propertySchemaList = JSONArray.parseArray(ontologyMap.get(entity.getOntologyId()).getPropertySchemaJson(), PropertySchema.class);
                    nodeVO.setLinkOntologyId(entity.getOntologyId());
                    nodeVO.setLinkOntologyName(ontologyMap.get(entity.getOntologyId()).getName());
                    nodeVO.setPropertySchemaJson(propertySchemaList);
                    nodeVO.setPropertyJson(entity.getPropertyJson());
                    nodeVO.setGraphType(getGraphType());
                    return nodeVO;
                }).toList();
        return PageResult.of(nodeVOList, page);
    }

    @Override
    public PageResult<RelationshipVO> pageRelationship(final RelationshipQueryVO queryVO) {
        Page<EntityRelationship> page = getEntityRelationshipDao().pageByQuery(queryVO);
        if (page.getTotal() == 0) {
            return PageResult.ofEmpty(page);
        }
        // 查询本体节点信息
        List<EntityRelationship> relationshipList = page.getRecords();
        Set<BigInteger> relationshipIdSet = relationshipList.stream()
                .flatMap(relationship -> Stream.of(relationship.getSourceNodeId(), relationship.getTargetNodeId()))
                .collect(Collectors.toSet());
        Map<BigInteger, String> entityMap = getEntityDao().listByIds(relationshipIdSet).stream()
                .collect(
                        Collectors.toMap(Entity::getId, Entity::getName)
                );
        // 查询本体关系信息
        Map<BigInteger, String> ontologyRelationshipMap = getOntologyRelationshipDao().listByIds(relationshipIdSet).stream()
                .collect(
                        Collectors.toMap(OntologyRelationship::getId, OntologyRelationship::getPropertySchemaJson)
                );
        List<RelationshipVO> relationshipVOList = relationshipList.stream()
                .map(relationship -> {
                    RelationshipVO relationshipVO = BeanCopyUtils.copy(relationship, RelationshipVO.class);
                    List<PropertySchema> propertySchemaJson = JSONArray.parseArray(
                            ontologyRelationshipMap.get(relationship.getOntologyRelationshipId()),
                            PropertySchema.class
                    );
                    relationshipVO.setGraphType(getGraphType());
                    relationshipVO.setPropertySchemaJson(propertySchemaJson);
                    relationshipVO.setPropertyJson(relationship.getPropertyJson());
                    relationshipVO.setSourceNodeName(entityMap.get(relationship.getSourceNodeId()));
                    relationshipVO.setTargetNodeName(entityMap.get(relationship.getTargetNodeId()));
                    return relationshipVO;
                }).toList();
        return PageResult.of(relationshipVOList, page);
    }

    @Override
    public PageResult<GraphVO> pageGraph(final GraphQueryVO queryVO) {
        Page<Graph> page = getGraphDao().pageByQuery(queryVO);
        if (page.getTotal() == 0) {
            return PageResult.ofEmpty(page);
        }

        // 查询本体图谱
        List<Graph> ontologyGraphList = getGraphDao()
                .getByType(queryVO.getOrganizationId(), GraphTypeEnum.ONTOLOGY);

        Map<BigInteger, String> linkedId = ontologyGraphList
                .stream()
                .collect(
                        Collectors.toMap(Graph::getId, Graph::getName)
                );

        List<GraphVO> graphVOList = page.getRecords().stream()
                .map(graph -> {
                    GraphVO graphVO = BeanCopyUtils.copy(graph, GraphVO.class);
                    graphVO.setLinkGraphName(linkedId.get(graph.getLinkGraphId()));
                    return graphVO;
                })
                .toList();

        return PageResult.of(graphVOList, page);
    }

    @Override
    public JsonGraphInfo doExportGraph(final JsonGraphInfo jsonGraphInfo) {
        // 查询关联的本体图谱
        Graph ontologyGraph = getGraphDao().getById(jsonGraphInfo.getGraph().getLinkGraphId());
        jsonGraphInfo.getGraph().setLinkOntologyGraph(ontologyGraph);
        // 查询实体信息
        List<Entity> entityList = getEntityDao().getByGraphId(jsonGraphInfo.getGraph().getId());
        List<EntityRelationship> entityRelationshipList = getEntityRelationshipDao()
                .getByGraphId(jsonGraphInfo.getGraph().getId());
        // 查询关联的本体信息
        Optional.ofNullable(entityList)
                .filter(list -> !list.isEmpty())
                .ifPresentOrElse(
                        list -> {
                            Set<BigInteger> ontologyIdSet = list.stream().map(Entity::getOntologyId).collect(Collectors.toSet());
                            List<Ontology> ontologyList = getOntologyDao().listByIds(ontologyIdSet);
                            jsonGraphInfo.setOntologyList(ontologyList);
                        },
                        () -> jsonGraphInfo.setOntologyList(Collections.emptyList()));

        Optional.ofNullable(entityRelationshipList)
                .filter(list -> !list.isEmpty())
                .ifPresentOrElse(
                        list -> {
                            Set<BigInteger> ontologyRelationshipIdSet = list.stream().map(EntityRelationship::getOntologyRelationshipId).collect(Collectors.toSet());
                            List<OntologyRelationship> ontologyRelationshipList = getOntologyRelationshipDao().listByIds(ontologyRelationshipIdSet);
                            jsonGraphInfo.setOntologyRelationshipList(ontologyRelationshipList);
                        },
                        () -> jsonGraphInfo.setOntologyRelationshipList(Collections.emptyList()));

        jsonGraphInfo.setEntityList(entityList);
        jsonGraphInfo.setEntityRelationshipList(entityRelationshipList);
        return jsonGraphInfo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public GraphDTO doImportGraph(
            final JsonGraphInfo jsonGraphInfo,
            final BigInteger organizationId
    ) {
        Graph oldEntityGraph = jsonGraphInfo.getGraph();
        if (Objects.isNull(oldEntityGraph)) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_IMPORT_INVALID);
        }
        if (Objects.isNull(oldEntityGraph.getLinkOntologyGraph())) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_IMPORT_INVALID);
        }
        // 保存本体图谱
        JsonGraphInfo ontologyJsonGraphInfo = BeanCopyUtils.copy(jsonGraphInfo, JsonGraphInfo.class);
        ontologyJsonGraphInfo.setGraph(ontologyJsonGraphInfo.getGraph().getLinkOntologyGraph());
        GraphDTO graphDTO = ontologyGraphStrategy.doImportGraph(ontologyJsonGraphInfo, organizationId);

        // 保存实体图谱
        GraphSaveVO oldEntityGraphSaveVO = BeanCopyUtils.copy(oldEntityGraph, GraphSaveVO.class);
        oldEntityGraphSaveVO.setLinkGraphId(new BigInteger(graphDTO.getDatabaseId().toString()));
        oldEntityGraphSaveVO.setOrganizationId(organizationId);
        oldEntityGraphSaveVO.setGraphType(GraphTypeEnum.ENTITY);
        GraphVO newEntityGraphVO = saveGraph(oldEntityGraphSaveVO);

        // 设置实体信息
        graphDTO.setDbPrefix(oldEntityGraphSaveVO.getGraphType().getDbPrefix());
        graphDTO.setDatabaseId(newEntityGraphVO.getId().longValue());

        // 保存实体信息
        GraphDTO entityGraphDTO = importNode(jsonGraphInfo.getEntityList(), newEntityGraphVO.getId(), graphDTO);
        GraphDTO resultDTO = importRelationship(jsonGraphInfo.getEntityRelationshipList(), newEntityGraphVO.getId(), entityGraphDTO);
        getChartStorageStrategy()
                .importNodeAndRelationshipBatch(resultDTO);
        return resultDTO;
    }

    /**
     * 导入节点, 返回节点新旧id映射.
     *
     * @param oldEntityList    旧实体信息
     * @param newEntityGraphId 新实体图谱id
     * @param graphDTO         图谱信息
     * @return 节点新旧id映射
     */
    private GraphDTO importNode(
            final List<Entity> oldEntityList,
            final BigInteger newEntityGraphId,
            final GraphDTO graphDTO
    ) {
        Map<BigInteger, BigInteger> entityIdMap = new HashMap<>();
        Map<BigInteger, Map<String, String>> ontologyIdMap = graphDTO.getOntologyIdMap();
        List<NodeDTO> nodeDTOList = new ArrayList<>();
        Optional.ofNullable(oldEntityList)
                .filter(list -> !list.isEmpty())
                .ifPresent(list -> {
                    List<Entity> entityList = list.stream().peek(entity -> {
                        BigInteger oldEntityId = entity.getId();
                        BigInteger oldOntologyId = entity.getOntologyId();
                        entity.setId(IdGenerator.generateKey());
                        entity.setGraphId(newEntityGraphId);
                        entity.setOntologyId(new BigInteger(ontologyIdMap.get(oldOntologyId).get(MapKeyConstant.NODE_ID)));
                        entity.init();
                        Map<String, Object> propertyJson = JsonSchemaValidator.validateAndGetMap(ontologyIdMap.get(oldOntologyId).get(MapKeyConstant.NODE_SCHEMA), entity.getPropertyJson());
                        entityIdMap.put(oldEntityId, entity.getId());
                        nodeDTOList.add(
                                NodeDTO.builder()
                                        .dbPrefix(getGraphType().getDbPrefix())
                                        .databaseId(newEntityGraphId.longValue())
                                        .id(entity.getId().longValue())
                                        .labels(List.of(ontologyIdMap.get(oldOntologyId).get(MapKeyConstant.NODE_NAME)))
                                        .properties(propertyJson)
                                        .build()
                        );
                    }).toList();
                    getEntityDao().saveBatch(entityList);
                });
        graphDTO.setNodeDTOList(nodeDTOList);
        graphDTO.setEntityIdMap(entityIdMap);
        return graphDTO;
    }

    /**
     * 导入关系, 节点新旧id映射.
     *
     * @param oldEntityRelationshipList 旧实体关系信息
     * @param newEntityGraphId          新实体图谱id
     * @param graphDTO                  图谱信息
     * @return 图谱信息
     */
    private GraphDTO importRelationship(
            final List<EntityRelationship> oldEntityRelationshipList,
            final BigInteger newEntityGraphId,
            final GraphDTO graphDTO
    ) {
        Map<BigInteger, Map<String, String>> ontologyRelationshipIdMap = graphDTO.getOntologyRelationshipIdMap();
        Map<BigInteger, BigInteger> entityIdMap = graphDTO.getEntityIdMap();
        List<RelationshipDTO> relationshipDTOList = new ArrayList<>();
        Optional.ofNullable(oldEntityRelationshipList)
                .filter(list -> !list.isEmpty())
                .ifPresent(list -> {
                    List<EntityRelationship> entityRelationshipList = list.stream()
                            .peek(relationship -> {
                                BigInteger oldOntologyRelationshipId = relationship.getOntologyRelationshipId();
                                relationship.setId(IdGenerator.generateKey());
                                relationship.setGraphId(newEntityGraphId);
                                relationship.setSourceNodeId(entityIdMap.get(relationship.getSourceNodeId()));
                                relationship.setTargetNodeId(entityIdMap.get(relationship.getTargetNodeId()));
                                relationship.setOntologyRelationshipId(new BigInteger(ontologyRelationshipIdMap.get(oldOntologyRelationshipId).get(MapKeyConstant.RELATIONSHIP_ID)));
                                relationship.init();
                                Map<String, Object> propertyJson = JsonSchemaValidator.validateAndGetMap(
                                        ontologyRelationshipIdMap.get(oldOntologyRelationshipId).get(MapKeyConstant.RELATIONSHIP_SCHEMA), relationship.getPropertyJson()
                                );
                                relationshipDTOList.add(
                                        RelationshipDTO.builder()
                                                .dbPrefix(getGraphType().getDbPrefix())
                                                .databaseId(relationship.getGraphId().longValue())
                                                .id(relationship.getId().longValue())
                                                .sourceId(relationship.getSourceNodeId().longValue())
                                                .targetId(relationship.getTargetNodeId().longValue())
                                                .name(ontologyRelationshipIdMap.get(oldOntologyRelationshipId).get(MapKeyConstant.RELATIONSHIP_NAME))
                                                .properties(propertyJson)
                                                .build()
                                );
                            }).toList();
                    getEntityRelationshipDao().saveBatch(entityRelationshipList);
                });
        graphDTO.setRelationshipDTOList(relationshipDTOList);
        return graphDTO;
    }

    @Override
    public NodeVO getNodeInfo(final BigInteger nodeId) {
        Entity entity = getEntityDao().getById(nodeId);
        Ontology ontology = getOntologyDao().getById(entity.getOntologyId());
        List<PropertySchema> propertySchemaList = JSONArray.parseArray(ontology.getPropertySchemaJson(), PropertySchema.class);
        NodeVO nodeVO = BeanCopyUtils.copy(entity, NodeVO.class);
        nodeVO.setLinkOntologyName(ontology.getName());
        nodeVO.setLinkOntologyId(ontology.getId());
        nodeVO.setPropertySchemaJson(propertySchemaList);
        return nodeVO;
    }

    @Override
    public RelationshipVO getRelationshipInfo(final BigInteger relationshipId) {
        EntityRelationship entityRelationship = getEntityRelationshipDao().getById(relationshipId);
        OntologyRelationship ontologyRelationship = getOntologyRelationshipDao().getById(entityRelationship.getOntologyRelationshipId());
        List<Entity> nodeList = getEntityDao().listByIds(
                Arrays.asList(
                        entityRelationship.getSourceNodeId(),
                        entityRelationship.getTargetNodeId()
                )
        );

        Map<BigInteger, String> nodeMap = nodeList.stream()
                .collect(
                        Collectors.toMap(Entity::getId, Entity::getName)
                );

        RelationshipVO relationshipVO = BeanCopyUtils.copy(entityRelationship, RelationshipVO.class);
        List<PropertySchema> propertySchemaList = JSONArray.parseArray(ontologyRelationship.getPropertySchemaJson(), PropertySchema.class);
        relationshipVO.setPropertySchemaJson(propertySchemaList);
        relationshipVO.setPropertyJson(entityRelationship.getPropertyJson());
        relationshipVO.setSourceNodeName(nodeMap.get(entityRelationship.getSourceNodeId()));
        relationshipVO.setTargetNodeName(nodeMap.get(entityRelationship.getTargetNodeId()));
        relationshipVO.setLinkOntologyId(entityRelationship.getOntologyRelationshipId());
        relationshipVO.setLinkOntologyName(ontologyRelationship.getName());
        return relationshipVO;
    }

    @Override
    public List<OptionalVO> getNodeOptional(final BigInteger graphId) {
        List<Entity> entityList = getEntityDao().getByGraphId(graphId);
        return BeanCopyUtils.copy(entityList, OptionalVO.class);
    }

    @Override
    public List<OptionalVO> getRelationshipOptional(final BigInteger graphId) {
        List<EntityRelationship> entityRelationshipList = getEntityRelationshipDao()
                .getByGraphId(graphId);
        return entityRelationshipList.stream()
                .map(entityRelationship -> {
                    OptionalVO optionalVO = new OptionalVO();
                    optionalVO.setId(entityRelationship.getId());
                    optionalVO.setName(entityRelationship.getName());
                    return optionalVO;
                }).toList();
    }

    @Override
    public void checkNodeExist(final NodeVO nodeVO) {
        LambdaQueryChainWrapper<Entity> queryWrapper = getEntityDao().lambdaQuery();
        if (Objects.nonNull(nodeVO.getId())) {
            queryWrapper.ne(Entity::getId, nodeVO.getId());
        }
        Entity entity = queryWrapper
                .eq(Entity::getGraphId, nodeVO.getGraphId())
                .eq(Entity::getName, nodeVO.getName())
                .one();
        if (Objects.nonNull(entity)) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_NODE_NAME_CONFLICT);
        }
    }

    @Override
    public NodeDTO doSaveNode(final NodeSaveVO nodeSaveVO) {
        // 查询本体节点
        Ontology ontology = getOntologyDao().getById(nodeSaveVO.getLinkOntologyId());
        // 实体节点属性JSON校验
        Map<String, Object> propertyJsonMap = JsonSchemaValidator.validateAndGetMap(ontology.getPropertySchemaJson(), nodeSaveVO.getPropertyJson());
        // 保存节点
        Entity entity = BeanCopyUtils.copy(nodeSaveVO, Entity.class);
        entity.setOntologyId(nodeSaveVO.getLinkOntologyId());
        entity.save();
        return NodeDTO.builder()
                .dbPrefix(getGraphType().getDbPrefix())
                .databaseId(entity.getGraphId().longValue())
                .id(entity.getId().longValue())
                .labels(List.of(ontology.getName()))
                .properties(propertyJsonMap)
                .build();
    }

    @Override
    public NodeDTO doUpdateNode(final NodeUpdateVO nodeUpdateVO) {
        // 查询本体节点
        Ontology ontology = getOntologyDao().getById(nodeUpdateVO.getLinkOntologyId());
        // 实体节点属性JSON校验
        Map<String, Object> propertyJsonMap = JsonSchemaValidator.validateAndGetMap(ontology.getPropertySchemaJson(), nodeUpdateVO.getPropertyJson());
        // 更新节点
        Entity entity = BeanCopyUtils.copy(nodeUpdateVO, Entity.class);
        entity.update();
        return NodeDTO.builder()
                .dbPrefix(getGraphType().getDbPrefix())
                .databaseId(entity.getGraphId().longValue())
                .id(entity.getId().longValue())
                .labels(List.of(ontology.getName()))
                .properties(propertyJsonMap)
                .build();
    }

    @Override
    public void doDeleteNode(final NodeDeleteVO nodeDeleteVO) {
        getEntityDao().removeBatchByIds(nodeDeleteVO.getIdList());
    }

    @Override
    public void checkRelationshipExist(final RelationshipVO relationshipVO) {
        EntityRelationship relationship = getEntityRelationshipDao().lambdaQuery()
                .eq(EntityRelationship::getSourceNodeId, relationshipVO.getSourceNodeId())
                .eq(EntityRelationship::getTargetNodeId, relationshipVO.getTargetNodeId())
                .eq(EntityRelationship::getName, relationshipVO.getName())
                .one();
        if (Objects.nonNull(relationship)) {
            throw new OntologyApiException(ApiErrorCode.GRAPH_RELATIONSHIP_CONFLICT);
        }
    }

    @Override
    public RelationshipDTO doSaveRelationship(final RelationshipSaveVO relationshipSaveVO) {
        // 查询本体关系
        OntologyRelationship ontologyRelationship = getOntologyRelationshipDao().getById(relationshipSaveVO.getLinkOntologyRelationshipId());
        Map<String, Object> propertyMap = JsonSchemaValidator.validateAndGetMap(ontologyRelationship.getPropertySchemaJson(), relationshipSaveVO.getPropertyJson());
        // 保存实体关系
        EntityRelationship entityRelationship = BeanCopyUtils.copy(relationshipSaveVO, EntityRelationship.class);
        entityRelationship.setOntologyRelationshipId(relationshipSaveVO.getLinkOntologyRelationshipId());
        entityRelationship.save();
        return RelationshipDTO.builder()
                .databaseId(entityRelationship.getGraphId().longValue())
                .dbPrefix(getGraphType().getDbPrefix())
                .id(entityRelationship.getId().longValue())
                .sourceId(entityRelationship.getSourceNodeId().longValue())
                .targetId(entityRelationship.getTargetNodeId().longValue())
                .name(ontologyRelationship.getName())
                .properties(propertyMap)
                .build();
    }

    @Override
    public RelationshipDTO doUpdateRelationship(final RelationshipUpdateVO relationshipUpdateVO) {
        // 查询本体关系
        OntologyRelationship ontologyRelationship = getOntologyRelationshipDao().getById(relationshipUpdateVO.getLinkOntologyRelationshipId());
        Map<String, Object> propertyMap = JsonSchemaValidator.validateAndGetMap(ontologyRelationship.getPropertySchemaJson(), relationshipUpdateVO.getPropertyJson());
        // 更新实体关系
        EntityRelationship entityRelationship = BeanCopyUtils.copy(relationshipUpdateVO, EntityRelationship.class);
        entityRelationship.setOntologyRelationshipId(relationshipUpdateVO.getLinkOntologyRelationshipId());
        entityRelationship.update();
        return RelationshipDTO.builder()
                .databaseId(entityRelationship.getGraphId().longValue())
                .dbPrefix(getGraphType().getDbPrefix())
                .id(entityRelationship.getId().longValue())
                .sourceId(entityRelationship.getSourceNodeId().longValue())
                .targetId(entityRelationship.getTargetNodeId().longValue())
                .name(ontologyRelationship.getName())
                .properties(propertyMap)
                .build();
    }

    @Override
    public void doDeleteRelationship(final RelationshipDeleteVO relationshipDelete) {
        // 删除实体关系
        getEntityRelationshipDao().removeBatchByIds(relationshipDelete.getIdList());
    }
}
