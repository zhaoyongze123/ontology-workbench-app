package com.devin.ontology.strategy.graph.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devin.ontology.common.constant.I18nConstant;
import com.devin.ontology.common.constant.MapKeyConstant;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.common.utils.BeanCopyUtils;
import com.devin.ontology.common.utils.IdGenerator;
import com.devin.ontology.common.utils.i18n.I18nUtils;
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
import com.devin.ontology.strategy.graph.GraphStrategyAbstract;
import com.devin.ontology.strategy.storage.ChartStorageStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * 本体图谱策略
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
public class OntologyGraphStrategy extends GraphStrategyAbstract {

    public OntologyGraphStrategy(
            final ChartStorageStrategy chartStorageStrategy,
            final GraphDao graphDao,
            final OrganizationDao organizationDao,
            final OntologyDao ontologyDao,
            final EntityDao entityDao,
            final OntologyRelationshipDao ontologyRelationshipDao,
            final EntityRelationshipDao entityRelationshipDao
    ) {
        super(chartStorageStrategy, graphDao, organizationDao, ontologyDao, entityDao, ontologyRelationshipDao, entityRelationshipDao);
    }

    @Override
    public GraphTypeEnum getGraphType() {
        return GraphTypeEnum.ONTOLOGY;
    }

    @Override
    public PageResult<NodeVO> pageNode(final NodeQueryVO queryVO) {
        Page<Ontology> page = getOntologyDao().pageByQuery(queryVO);
        if (page.getTotal() == 0) {
            return PageResult.ofEmpty(page);
        }
        List<NodeVO> nodeVOList = page.getRecords().stream()
                .map(ontology -> {
                    NodeVO nodeVO = BeanCopyUtils.copy(ontology, NodeVO.class);
                    List<PropertySchema> propertySchemaList = JSONArray.parseArray(ontology.getPropertySchemaJson(), PropertySchema.class);
                    String propertyJson = JSON.toJSONString(convertProperty(propertySchemaList));
                    nodeVO.setPropertySchemaJson(propertySchemaList);
                    nodeVO.setPropertyJson(propertyJson);
                    nodeVO.setGraphType(getGraphType());
                    return nodeVO;
                }).toList();
        return PageResult.of(nodeVOList, page);
    }

    @Override
    public PageResult<RelationshipVO> pageRelationship(final RelationshipQueryVO queryVO) {
        Page<OntologyRelationship> page = getOntologyRelationshipDao().pageByQuery(queryVO);
        if (page.getTotal() == 0) {
            return PageResult.ofEmpty(page);
        }
        // 查询本体节点信息
        List<OntologyRelationship> relationshipList = page.getRecords();
        Set<BigInteger> relationshipIdSet = relationshipList.stream()
                .flatMap(relationship -> Stream.of(relationship.getSourceNodeId(), relationship.getTargetNodeId()))
                .collect(Collectors.toSet());
        Map<BigInteger, String> ontologyMap = getOntologyDao().listByIds(relationshipIdSet).stream()
                .collect(
                        Collectors.toMap(Ontology::getId, Ontology::getName)
                );
        List<RelationshipVO> relationshipVOList = relationshipList.stream()
                .map(relationship -> {
                    RelationshipVO relationshipVO = BeanCopyUtils.copy(relationship, RelationshipVO.class);
                    List<PropertySchema> propertySchemaJson = JSONArray.parseArray(relationship.getPropertySchemaJson(), PropertySchema.class);
                    String propertyJson = JSON.toJSONString(convertProperty(propertySchemaJson));
                    relationshipVO.setGraphType(getGraphType());
                    relationshipVO.setPropertySchemaJson(propertySchemaJson);
                    relationshipVO.setPropertyJson(propertyJson);
                    relationshipVO.setSourceNodeName(ontologyMap.get(relationship.getSourceNodeId()));
                    relationshipVO.setTargetNodeName(ontologyMap.get(relationship.getTargetNodeId()));
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

        // 查询实体图谱
        List<Graph> entityGraphList = getGraphDao()
                .getByType(queryVO.getOrganizationId(), GraphTypeEnum.ENTITY);

        Set<BigInteger> linkedId = entityGraphList
                .stream()
                .map(Graph::getLinkGraphId)
                .collect(Collectors.toSet());

        List<GraphVO> graphVOList = page.getRecords().stream()
                .map(graph -> {
                    GraphVO graphVO = BeanCopyUtils.copy(graph, GraphVO.class);
                    graphVO.setHasEntity(linkedId.contains(graph.getId()));
                    return graphVO;
                })
                .toList();

        return PageResult.of(graphVOList, page);
    }

    @Override
    public NodeVO getNodeInfo(final BigInteger nodeId) {
        Ontology ontology = getOntologyDao().getById(nodeId);
        NodeVO nodeVO = BeanCopyUtils.copy(ontology, NodeVO.class);
        String propertyJson = JSON.toJSONString(convertProperty(nodeVO.getPropertySchemaJson()));
        nodeVO.setPropertyJson(propertyJson);
        return nodeVO;
    }

    @Override
    public RelationshipVO getRelationshipInfo(final BigInteger relationshipId) {
        OntologyRelationship relationship = getOntologyRelationshipDao().getById(relationshipId);
        RelationshipVO relationshipVO = BeanCopyUtils.copy(relationship, RelationshipVO.class);
        List<PropertySchema> propertySchemaList = JSONArray.parseArray(relationship.getPropertySchemaJson(), PropertySchema.class);
        String propertyJson = JSON.toJSONString(convertProperty(propertySchemaList));
        relationshipVO.setPropertySchemaJson(propertySchemaList);
        relationshipVO.setPropertyJson(propertyJson);
        return relationshipVO;
    }

    @Override
    public List<OptionalVO> getNodeOptional(final BigInteger graphId) {
        List<Ontology> ontologyList = getOntologyDao().getByGraphId(graphId);
        return BeanCopyUtils.copy(ontologyList, OptionalVO.class);
    }

    @Override
    public List<OptionalVO> getRelationshipOptional(final BigInteger graphId) {
        List<OntologyRelationship> ontologyRelationshipList = getOntologyRelationshipDao()
                .getByGraphId(graphId);
        return ontologyRelationshipList.stream()
                .map(relationship -> {
                    OptionalVO optionalVO = new OptionalVO();
                    optionalVO.setId(relationship.getId());
                    optionalVO.setName(relationship.getName());
                    return optionalVO;
                }).toList();
    }

    @Override
    public void checkNodeExist(final NodeVO nodeVO) {
        LambdaQueryChainWrapper<Ontology> queryWrapper = getOntologyDao().lambdaQuery();
        if (Objects.nonNull(nodeVO.getId())) {
            queryWrapper.ne(Ontology::getId, nodeVO.getId());
        }
        Ontology ontology = queryWrapper
                .eq(Ontology::getGraphId, nodeVO.getGraphId())
                .eq(Ontology::getName, nodeVO.getName())
                .one();
        if (Objects.nonNull(ontology)) {
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.NODE_IS_EXIST));
        }
    }

    @Override
    public NodeDTO doSaveNode(final NodeSaveVO nodeSaveVO) {
        Ontology ontology = BeanCopyUtils.copy(nodeSaveVO, Ontology.class)
                .save();
        return NodeDTO.builder()
                .dbPrefix(getGraphType().getDbPrefix())
                .databaseId(ontology.getGraphId().longValue())
                .id(ontology.getId().longValue())
                .labels(List.of(nodeSaveVO.getName()))
                .properties(convertProperty(nodeSaveVO.getPropertySchemaJson()))
                .build();
    }

    @Override
    public NodeDTO doUpdateNode(final NodeUpdateVO nodeUpdateVO) {
        // 更新节点
        Ontology ontology = BeanCopyUtils.copy(nodeUpdateVO, Ontology.class);
        ontology.update();
        return NodeDTO.builder()
                .dbPrefix(getGraphType().getDbPrefix())
                .databaseId(ontology.getGraphId().longValue())
                .id(ontology.getId().longValue())
                .labels(List.of(nodeUpdateVO.getName()))
                .properties(convertProperty(nodeUpdateVO.getPropertySchemaJson()))
                .build();
    }

    @Override
    public void doDeleteNode(final NodeDeleteVO nodeDeleteVO) {
        // 判断实体图谱中是否关联该节点
        Long count = getEntityDao().lambdaQuery()
                .in(Entity::getOntologyId, nodeDeleteVO.getIdList())
                .count();
        if (count > 0) {
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.NODE_HAVE_DEPENDENCY));
        }
        getOntologyDao().removeBatchByIds(nodeDeleteVO.getIdList());
    }

    @Override
    public void checkRelationshipExist(final RelationshipVO relationshipVO) {
        OntologyRelationship relationship = getOntologyRelationshipDao().lambdaQuery()
                .eq(OntologyRelationship::getSourceNodeId, relationshipVO.getSourceNodeId())
                .eq(OntologyRelationship::getTargetNodeId, relationshipVO.getTargetNodeId())
                .eq(OntologyRelationship::getName, relationshipVO.getName())
                .one();
        if (Objects.nonNull(relationship)) {
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.RELATIONSHIP_EXIST));
        }
    }

    @Override
    public RelationshipDTO doSaveRelationship(final RelationshipSaveVO relationshipSaveVO) {
        OntologyRelationship relationship = BeanCopyUtils.copy(relationshipSaveVO, OntologyRelationship.class);
        relationship.save();
        return RelationshipDTO.builder()
                .databaseId(relationship.getGraphId().longValue())
                .dbPrefix(getGraphType().getDbPrefix())
                .id(relationship.getId().longValue())
                .sourceId(relationship.getSourceNodeId().longValue())
                .targetId(relationship.getTargetNodeId().longValue())
                .name(relationship.getName())
                .properties(convertProperty(relationshipSaveVO.getPropertySchemaJson()))
                .build();
    }

    @Override
    public RelationshipDTO doUpdateRelationship(final RelationshipUpdateVO relationshipUpdateVO) {
        OntologyRelationship relationship = BeanCopyUtils.copy(relationshipUpdateVO, OntologyRelationship.class);
        relationship.update();
        return RelationshipDTO.builder()
                .databaseId(relationship.getGraphId().longValue())
                .dbPrefix(getGraphType().getDbPrefix())
                .id(relationship.getId().longValue())
                .sourceId(relationship.getSourceNodeId().longValue())
                .targetId(relationship.getTargetNodeId().longValue())
                .name(relationship.getName())
                .properties(convertProperty(relationshipUpdateVO.getPropertySchemaJson()))
                .build();
    }

    @Override
    public void doDeleteRelationship(final RelationshipDeleteVO relationshipDelete) {
        // 检查是否存在关联的本体关系
        Set<BigInteger> ontologyRelationshipIdSet = new HashSet<>(relationshipDelete.getIdList());
        Long count = getEntityRelationshipDao().lambdaQuery()
                .in(EntityRelationship::getOntologyRelationshipId, ontologyRelationshipIdSet)
                .count();
        if (count > 0) {
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.RELATIONSHIP_HAVE_ENTITY));
        }

        // 删除本体关系
        getOntologyRelationshipDao().removeBatchByIds(ontologyRelationshipIdSet);
    }

    @Override
    public JsonGraphInfo doExportGraph(final JsonGraphInfo jsonGraphInfo) {
        // 查询图谱下的所有本体信息
        List<Ontology> ontologyList = getOntologyDao().getByGraphId(jsonGraphInfo.getGraph().getId());
        List<OntologyRelationship> ontologyRelationshipList = getOntologyRelationshipDao()
                .getByGraphId(jsonGraphInfo.getGraph().getId());
        jsonGraphInfo.setOntologyList(ontologyList);
        jsonGraphInfo.setOntologyRelationshipList(ontologyRelationshipList);
        return jsonGraphInfo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public GraphDTO doImportGraph(
            final JsonGraphInfo jsonGraphInfo,
            final BigInteger organizationId
    ) {
        GraphSaveVO graphSaveVO = BeanCopyUtils.copy(jsonGraphInfo.getGraph(), GraphSaveVO.class);
        graphSaveVO.setGraphType(getGraphType());
        graphSaveVO.setOrganizationId(organizationId);
        GraphVO graphVO = saveGraph(graphSaveVO);

        // 本体新旧id映射Map<oldId, {"node_id", "node_name"}>
        GraphDTO graphDTO = importNode(jsonGraphInfo.getOntologyList(), graphVO.getId());
        graphDTO.setDbPrefix(getGraphType().getDbPrefix());
        graphDTO.setDatabaseId(graphVO.getId().longValue());

        // 批量保存本体关系信息
        GraphDTO resultDTO = importRelationship(jsonGraphInfo.getOntologyRelationshipList(), graphVO.getId(), graphDTO);
        getChartStorageStrategy()
                .importNodeAndRelationshipBatch(resultDTO);
        return resultDTO;
    }

    /**
     * 导入节点信息, 返回本体新旧id映射.
     *
     * @param oldOntologyList    旧本体信息
     * @param newOntologyGraphId 新本体图谱id
     * @return 本体新旧id映射
     */
    protected GraphDTO importNode(
            final List<Ontology> oldOntologyList,
            final BigInteger newOntologyGraphId
    ) {
        Map<BigInteger, Map<String, String>> ontologyIdMap = new HashMap<>();
        GraphDTO graphDTO = new GraphDTO();
        List<NodeDTO> nodeDTOList = new ArrayList<>();
        // 批量保存本体信息
        Optional.of(oldOntologyList)
                .filter(list -> !list.isEmpty())
                .ifPresent(list -> {
                    List<Ontology> ontologyList = list
                            .stream().peek(ontology -> {
                                BigInteger ontologyOldId = ontology.getId();
                                ontology.setId(IdGenerator.generateKey());
                                ontology.setGraphId(newOntologyGraphId);
                                ontology.init();
                                ontologyIdMap.put(ontologyOldId, Map.of(
                                        MapKeyConstant.NODE_ID, ontology.getId().toString(),
                                        MapKeyConstant.NODE_NAME, ontology.getName(),
                                        MapKeyConstant.NODE_SCHEMA, ontology.getPropertySchemaJson()
                                ));
                                List<PropertySchema> propertySchemaList = JSONArray.parseArray(ontology.getPropertySchemaJson(), PropertySchema.class);
                                nodeDTOList.add(
                                        NodeDTO.builder()
                                                .dbPrefix(getGraphType().getDbPrefix())
                                                .databaseId(ontology.getGraphId().longValue())
                                                .id(ontology.getId().longValue())
                                                .labels(List.of(ontology.getName()))
                                                .properties(convertProperty(propertySchemaList))
                                                .build()
                                );
                            }).toList();
                    getOntologyDao().saveBatch(ontologyList);
                    graphDTO.setOntologyIdMap(ontologyIdMap);
                });
        graphDTO.setNodeDTOList(nodeDTOList);
        return graphDTO;
    }

    /**
     * 导入本体关系, 返回本体关系新旧id映射.
     *
     * @param oldOntologyRelationshipList 旧本体关系列表
     * @param newOntologyGraphId          新本体图谱id
     * @param graphDTO                    图谱信息
     * @return 本体关系新旧id映射
     */
    protected GraphDTO importRelationship(
            final List<OntologyRelationship> oldOntologyRelationshipList,
            final BigInteger newOntologyGraphId,
            final GraphDTO graphDTO
    ) {
        Map<BigInteger, Map<String, String>> ontologyRelationshipIdMap = new HashMap<>();
        Map<BigInteger, Map<String, String>> ontologyIdMap = graphDTO.getOntologyIdMap();
        List<RelationshipDTO> relationshipDTOList = new ArrayList<>();
        Optional.ofNullable(oldOntologyRelationshipList)
                .filter(list -> !list.isEmpty())
                .ifPresent(list -> {
                    List<OntologyRelationship> ontologyRelationshipList = list.stream()
                            .peek(ontologyRelationship -> {
                                BigInteger oldRelationshipId = ontologyRelationship.getId();
                                BigInteger oldSourceNodeId = ontologyRelationship.getSourceNodeId();
                                BigInteger oldTargetNodeId = ontologyRelationship.getTargetNodeId();
                                ontologyRelationship.setId(IdGenerator.generateKey());
                                ontologyRelationship.setGraphId(newOntologyGraphId);
                                ontologyRelationship.setSourceNodeId(new BigInteger(ontologyIdMap.get(oldSourceNodeId).get(MapKeyConstant.NODE_ID)));
                                ontologyRelationship.setTargetNodeId(new BigInteger(ontologyIdMap.get(oldTargetNodeId).get(MapKeyConstant.NODE_ID)));
                                ontologyRelationship.init();
                                List<PropertySchema> propertySchemaList = JSONArray.parseArray(ontologyRelationship.getPropertySchemaJson(), PropertySchema.class);
                                ontologyRelationshipIdMap.put(oldRelationshipId, Map.of(
                                        MapKeyConstant.RELATIONSHIP_ID, ontologyRelationship.getId().toString(),
                                        MapKeyConstant.RELATIONSHIP_NAME, ontologyRelationship.getName(),
                                        MapKeyConstant.RELATIONSHIP_SCHEMA, ontologyRelationship.getPropertySchemaJson()
                                ));

                                relationshipDTOList.add(
                                        RelationshipDTO.builder()
                                                .dbPrefix(getGraphType().getDbPrefix())
                                                .databaseId(ontologyRelationship.getGraphId().longValue())
                                                .id(ontologyRelationship.getId().longValue())
                                                .name(ontologyRelationship.getName())
                                                .sourceId(ontologyRelationship.getSourceNodeId().longValue())
                                                .targetId(ontologyRelationship.getTargetNodeId().longValue())
                                                .properties(convertProperty(propertySchemaList))
                                                .build()
                                );
                            }).toList();
                    getOntologyRelationshipDao().saveBatch(ontologyRelationshipList);
                });
        graphDTO.setRelationshipDTOList(relationshipDTOList);
        graphDTO.setOntologyRelationshipIdMap(ontologyRelationshipIdMap);
        return graphDTO;
    }

    /**
     * 获取实体关联的图.
     *
     * @param entityGraphId 实体图ID
     * @return 实体关联的图
     */
    public List<NodeVO> getLinkOntologyByEntityGraphId(
            final BigInteger entityGraphId
    ) {
        // 获取实体关联的本体图谱
        Graph entityGraph = getGraphDao()
                .lambdaQuery()
                .select(Graph::getLinkGraphId)
                .eq(Graph::getType, GraphTypeEnum.ENTITY.name())
                .eq(Graph::getId, entityGraphId)
                .one();
        List<Ontology> ontologyList = getOntologyDao()
                .lambdaQuery()
                .eq(Ontology::getGraphId, entityGraph.getLinkGraphId())
                .list();

        // 封装VO对象
        return ontologyList.stream()
                .map(ontology -> {
                    NodeVO nodeVO = new NodeVO();
                    nodeVO.setId(ontology.getId());
                    nodeVO.setName(ontology.getName());
                    return nodeVO;
                }).toList();
    }

    /**
     * 获取实体关联的本体关系.
     *
     * @param sourceNodeId 源节点ID
     * @param targetNodeId 目标节点ID
     * @return 实体关联的本体关系
     */
    public List<OptionalVO> getLinkOntologyRelationshipList(
            final BigInteger sourceNodeId,
            final BigInteger targetNodeId
    ) {
        Map<BigInteger, BigInteger> ontologyNodeIdMap = getEntityDao()
                .lambdaQuery()
                .select(Entity::getId, Entity::getOntologyId)
                .in(Entity::getId, List.of(sourceNodeId, targetNodeId))
                .list()
                .stream()
                .collect(
                        Collectors.toMap(Entity::getId, Entity::getOntologyId)
                );

        List<OntologyRelationship> ontologyRelationshipList = getOntologyRelationshipDao()
                .lambdaQuery()
                .eq(OntologyRelationship::getSourceNodeId, ontologyNodeIdMap.get(sourceNodeId))
                .eq(OntologyRelationship::getTargetNodeId, ontologyNodeIdMap.get(targetNodeId))
                .orderByDesc(OntologyRelationship::getUpdateTime)
                .list();

        return BeanCopyUtils.copy(ontologyRelationshipList, OptionalVO.class);
    }
}
