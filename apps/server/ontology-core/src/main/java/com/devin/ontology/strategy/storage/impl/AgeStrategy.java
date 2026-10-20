package com.devin.ontology.strategy.storage.impl;

import com.alibaba.fastjson2.JSONArray;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.exception.ClientException;
import com.devin.ontology.dao.EntityRelationshipDao;
import com.devin.ontology.dao.OntologyRelationshipDao;
import com.devin.ontology.domain.entity.EntityRelationship;
import com.devin.ontology.domain.entity.OntologyRelationship;
import com.devin.ontology.strategy.storage.CypherUtils;
import com.devin.ontology.strategy.storage.AgeSql;
import com.devin.ontology.domain.dto.GraphDTO;
import com.devin.ontology.domain.dto.NodeDTO;
import com.devin.ontology.domain.dto.RelationshipDTO;
import com.devin.ontology.strategy.storage.ChartStorageStrategyAbstract;
import com.devin.ontology.common.constant.I18nConstant;
import com.devin.ontology.common.enums.ValueType;
import com.devin.ontology.common.exception.BusinessException;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.common.utils.i18n.I18nUtils;
import com.devin.ontology.common.utils.r.ResultEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 2025/9/23 18:48.
 *
 * <p>
 * Age策略类，SQL 失败交由外层事务回滚，避免在已中止的事务内重试。
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "graph.database.type", havingValue = "age")
public class AgeStrategy extends ChartStorageStrategyAbstract {

    /**
     * 批量导入的批次大小（每批处理的节点/关系数量）.
     */
    private static final int BATCH_SIZE = 20;

    /**
     * Age加载错误信息.
     */
    private static final String AGE_LOAD_ERROR_MSG = "unhandled cypher(cstring) function call";

    /**
     * 关系标签前缀.
     */
    private static final String AGE_RELATIONSHIP_LABEL_PREFIX = "relationship_";

    /**
     * 数据库访问组件。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * AGE 返回值的 JSON 转换组件。
     */
    private final ObjectMapper objectMapper;

    /**
     * 本体关系数据访问组件。
     */
    private final OntologyRelationshipDao ontologyRelationshipDao;

    /**
     * 实体关系数据访问组件。
     */
    private final EntityRelationshipDao entityRelationshipDao;

    /**
     * 创建图库。
     *
     * @param dbPrefix 图库名称前缀
     * @param id 图库编号
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void createDatabase(final String dbPrefix, final Long id) {
        try {
            String load = """
                        SET search_path = ag_catalog, "$user", ontology;
                    """;
            jdbcTemplate.execute(load);
            AgeSql.executePrepared(jdbcTemplate,
                    "SELECT ag_catalog.create_graph(?)", AgeSql.graphName(dbPrefix, id));
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "创建图库", e);
        } catch (Exception e) {
            throw storageFailure(ResultEnum.GRAPH_ERROR, "创建图库", e);
        }
    }

    /**
     * 删除图库。
     *
     * @param ids 图库编号集合
     * @param dbPrefix 图库名称前缀
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void dropDatabase(final Collection<Long> ids, final String dbPrefix) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String load = """
                    SET search_path = ag_catalog, "$user", ontology;
                """;
        jdbcTemplate.execute(load);
        try {
            ids.forEach(id -> {
                AgeSql.executePrepared(jdbcTemplate, "SELECT ag_catalog.drop_graph(?, true)",
                        AgeSql.graphName(dbPrefix, id));
            });
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "删除图库", e);
        }
    }

    /**
     * 创建节点。
     *
     * @param nodeDTO 节点信息
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void createNode(final NodeDTO nodeDTO) {
        String load = """
                    SET search_path = ag_catalog, "$user", ontology;
                """;
        jdbcTemplate.execute(load);

        String cypher = CypherUtils.generateCreateNodeCypher(nodeDTO);

        try {

            String sql = AgeSql.cypherSql(AgeSql.graphName(nodeDTO.getDbPrefix(), nodeDTO.getDatabaseId()),
                    cypher, List.of("n"));
            jdbcTemplate.execute(sql);
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "创建节点", e);
        }
    }

    /**
     * 分批创建节点。
     *
     * @param nodeDTOList 节点列表
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void createNodeBatch(final List<NodeDTO> nodeDTOList) {
        if (nodeDTOList == null || nodeDTOList.isEmpty()) {
            return;
        }

        // 校验同库，并返回数据库id
        Long databaseId = checkDatabaseEquals(nodeDTOList);

        String load = """
                    SET search_path = ag_catalog, "$user", ontology;
                """;
        jdbcTemplate.execute(load);

        String database = AgeSql.graphName(nodeDTOList.get(0).getDbPrefix(), databaseId);
        int totalSize = nodeDTOList.size();

        // 分批处理节点，避免 SQL 语句过长
        try {
            for (int i = 0; i < totalSize; i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, totalSize);
                List<NodeDTO> batch = nodeDTOList.subList(i, endIndex);

                String sql = AgeSql.cypherSql(database,
                        CypherUtils.generateCreateNodeBatchCypher(batch), List.of("n"));

                jdbcTemplate.execute(sql);
            }
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "批量创建节点", e);
        }
    }

    /**
     * 创建关系。
     *
     * @param relationshipDTO 关系信息
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void createRelationship(final RelationshipDTO relationshipDTO) {
        String load = """
                    SET search_path = ag_catalog, "$user", ontology;
                """;
        jdbcTemplate.execute(load);
        checkNodeExist(relationshipDTO.getSourceId(), AgeSql.graphName(relationshipDTO.getDbPrefix(), relationshipDTO.getDatabaseId()));
        checkNodeExist(relationshipDTO.getTargetId(), AgeSql.graphName(relationshipDTO.getDbPrefix(), relationshipDTO.getDatabaseId()));

        String cypher = CypherUtils.generateCreateRelationshipCypher(relationshipDTO.getName(),
                relationshipDTO.getId(), relationshipDTO.getSourceId(), relationshipDTO.getTargetId(),
                relationshipDTO.getProperties());
        String sql = AgeSql.cypherSql(AgeSql.graphName(relationshipDTO.getDbPrefix(), relationshipDTO.getDatabaseId()),
                cypher, List.of("n"));

        // 创建关系
        try {
            jdbcTemplate.execute(sql);
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "创建关系", e);
        }
    }

    /**
     * 批量删除节点。
     *
     * @param ids 节点编号列表
     * @param databaseId 图库编号
     * @param dbPrefix 图库名称前缀
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteNodeBatch(final List<Long> ids, final Long databaseId, final String dbPrefix) {
        try {
            String load = """
                        SET search_path = ag_catalog, "$user", ontology;
                    """;
            jdbcTemplate.execute(load);

            String cypher = "MATCH (n) WHERE n._id IN " + AgeSql.cypherIntegerList(ids)
                    + " DETACH DELETE n RETURN n";
            String sql = AgeSql.cypherSql(AgeSql.graphName(dbPrefix, databaseId), cypher, List.of("n"));

            List<Map<String, Object>> delResult = jdbcTemplate.queryForList(sql);

            Set<String> labelSet = delResult.stream()
                    .map(r -> Optional.ofNullable(r.get("n"))
                            .map(n -> {
                                String value = ((PGobject) n).getValue();
                                try {
                                    return (String) objectMapper.readValue(value, Map.class).get("label");
                                } catch (JsonProcessingException e) {
                                    throw new BusinessException(ResultEnum.PARAM_IS_INVALID);
                                }
                            }).orElse(null)
                    )
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 对每个 label，检查是否还有其他节点在使用
            for (String label : labelSet) {
                if (isLabelStillInUse(label, AgeSql.graphName(dbPrefix, databaseId))) {
                    dropLabel(label, AgeSql.graphName(dbPrefix, databaseId));
                }
            }
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "批量删除节点", e);
        } catch (Exception e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "批量删除节点", e);
        }
    }

    /**
     * 更新节点。
     *
     * @param nodeDTO 节点信息
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void editNode(final NodeDTO nodeDTO) {
        // 判断节点是否是原节点label
        String load = """
                    SET search_path = ag_catalog, "$user", ontology;
                """;
        jdbcTemplate.execute(load);
        String cypher = findNodeById(nodeDTO.getId(), nodeDTO.getDatabaseId(), nodeDTO.getDbPrefix())
                .map(node -> {
                    List<String> labels = JSONArray.parseArray((String) node.get("labels"), String.class);
                    String oldLabelStr = CypherUtils.generateLabelStr(labels);
                    String newLabelStr = CypherUtils.generateLabelStr(nodeDTO.getLabels());

                    if (oldLabelStr.equals(newLabelStr)) {
                        long nodeId = AgeSql.cypherInteger(nodeDTO.getId());
                        return "MERGE (node" + oldLabelStr + " {_id: " + nodeId + "}) "
                                + "SET node = " + CypherUtils.convertPropertyVal(nodeDTO.getProperties())
                                + ", node._id = " + nodeId + " RETURN node._id as id";
                    } else {
                        throw new BusinessException(ResultEnum.NODE_ERROR, "节点标签不可修改");
                    }
                }).orElseThrow(() -> new BusinessException(ResultEnum.NODE_ERROR, I18nUtils.getMessage(I18nConstant.NODE_NOT_EXIST)));

        String sql = AgeSql.cypherSql(AgeSql.graphName(nodeDTO.getDbPrefix(), nodeDTO.getDatabaseId()),
                cypher, List.of("n"));

        try {
            jdbcTemplate.execute(sql);
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "更新节点", e);
        }
    }

    /**
     * 按编号查询节点。
     *
     * @param id 节点编号
     * @param databaseId 图库编号
     * @param dbPrefix 图库名称前缀
     * @return 节点查询结果
     */
    @Override
    public Optional<Map<String, Object>> findNodeById(final Long id, final Long databaseId, final String dbPrefix) {
        String load = """
                    SET search_path = ag_catalog, "$user", ontology;
                """;
        jdbcTemplate.execute(load);
        String sql = AgeSql.cypherSql(AgeSql.graphName(dbPrefix, databaseId),
                "MATCH (n) WHERE n._id = " + AgeSql.cypherInteger(id)
                        + " RETURN n, labels(n) as labels, n._id as id",
                List.of("n", "labels", "id"));

        Map<String, Object> resultMap = new HashMap<>();

        try {
            resultMap = jdbcTemplate.queryForMap(sql);
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "查询节点", e);
        }

        return Optional.of(Map.of(
                "id", Objects.requireNonNull(((PGobject) resultMap.get("id")).getValue()),
                "labels", Objects.requireNonNull(((PGobject) resultMap.get("labels")).getValue()),
                "n", Objects.requireNonNull(((PGobject) resultMap.get("n")).getValue())
        ));
    }

    /**
     * 批量删除关系。
     *
     * @param ids 关系编号列表
     * @param databaseId 图库编号
     * @param dbPrefix 图库名称前缀
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteRelationshipBatch(final List<Long> ids, final Long databaseId, final String dbPrefix) {
        try {
            String load = """
                        SET search_path = ag_catalog, "$user", ontology;
                    """;
            jdbcTemplate.execute(load);

        String cypher = "MATCH ()-[r]->() WHERE r._id IN " + AgeSql.cypherIntegerList(ids)
                + " DELETE r RETURN r";
        String sql = AgeSql.cypherSql(AgeSql.graphName(dbPrefix, databaseId), cypher, List.of("r"));

            List<Map<String, Object>> delResult = jdbcTemplate.queryForList(sql);

            Set<String> labelSet = delResult.stream()
                    .map(res -> Optional.ofNullable(res.get("r"))
                            .map(r -> {
                                String value = ((PGobject) r).getValue();
                                try {
                                    return (String) objectMapper.readValue(value, Map.class).get("label");
                                } catch (JsonProcessingException e) {
                                    throw new BusinessException(ResultEnum.PARAM_IS_INVALID);
                                }
                            }).orElse(null)
                    )
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 对每个 label，检查是否还有其他关系在使用
            for (String label : labelSet) {
                if (isLabelStillInUse(label, AgeSql.graphName(dbPrefix, databaseId))) {
                    dropLabel(label, AgeSql.graphName(dbPrefix, databaseId));
                }
            }
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "批量删除关系", e);
        } catch (Exception e) {
            throw storageFailure(ResultEnum.RELATIONSHIP_ERROR, "批量删除关系", e);
        }
    }

    /**
     * 更新关系。
     *
     * @param relationshipDTO 关系信息
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void editRelationship(final RelationshipDTO relationshipDTO) {
        String load = """
                    SET search_path = ag_catalog, "$user", ontology;
                """;
        jdbcTemplate.execute(load);

        String database = AgeSql.graphName(relationshipDTO.getDbPrefix(), relationshipDTO.getDatabaseId());

        try {
            // 第一步：获取关系信息
            String getInfoSql = AgeSql.cypherSql(database,
                    "MATCH (startNode)-[r]->(endNode) WHERE r._id = "
                            + AgeSql.cypherInteger(relationshipDTO.getId())
                            + " RETURN id(startNode) as startId, id(endNode) as endId, type(r) as oldLabel",
                    List.of("startId", "endId", "oldLabel"));

            Map<String, Object> relInfo = jdbcTemplate.queryForObject(getInfoSql, (rs, rowNum) -> {
                String startIdStr = rs.getString("startId");
                String endIdStr = rs.getString("endId");
                String oldLabelStr = rs.getString("oldLabel");

                Map<String, Object> info = new HashMap<>();
                info.put("startId", Long.parseLong(startIdStr.replaceAll("::.*", "").trim()));
                info.put("endId", Long.parseLong(endIdStr.replaceAll("::.*", "").trim()));
                info.put("oldLabel", oldLabelStr.replaceAll("\"", "").trim());
                return info;
            });

            if (relInfo == null) {
                throw new ValidateException(I18nUtils.getMessage(I18nConstant.RELATIONSHIP_NOT_EXIST));
            }

            String oldLabel = (String) relInfo.get("oldLabel");

            // 第二步：删除关系
            String deleteSql = AgeSql.cypherSql(database,
                    "MATCH ()-[r]->() WHERE r._id = " + AgeSql.cypherInteger(relationshipDTO.getId())
                            + " DELETE r RETURN 1 as deleted",
                    List.of("deleted"));

            jdbcTemplate.queryForObject(deleteSql, (rs, rowNum) -> rs.getInt("deleted"));

            // 第三步：删除旧label
            try {
                AgeSql.executePrepared(jdbcTemplate, "SELECT ag_catalog.drop_label(?, ?)", database, oldLabel);
            } catch (Exception e) {
                throw storageFailure(ResultEnum.RELATIONSHIP_ERROR, "删除旧关系标签", e);
            }

            // 第四步：创建新关系
            String createRelCypher = "MATCH (startNode), (endNode) WHERE startNode._id = "
                    + AgeSql.cypherInteger(relationshipDTO.getSourceId()) + " AND endNode._id = "
                    + AgeSql.cypherInteger(relationshipDTO.getTargetId()) + " CREATE (startNode)-[new:"
                    + CypherUtils.quoteIdentifier(relationshipDTO.getName()) + "]->(endNode) SET new = "
                    + CypherUtils.convertPropertyVal(relationshipDTO.getProperties()) + " SET new._id = "
                    + AgeSql.cypherInteger(relationshipDTO.getId()) + " RETURN id(new) as newId";
            String createRelSql = AgeSql.cypherSql(database, createRelCypher, List.of("newId"));

            jdbcTemplate.execute(createRelSql);
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "更新关系", e);
        }
    }

    /**
     * 按编号查询关系。
     *
     * @param id 关系编号
     * @param databaseId 图库编号
     * @param dbPrefix 图库名称前缀
     * @return 关系查询结果
     */
    @Override
    public Optional<Map<String, Object>> findRelationshipById(final Long id, final Long databaseId, final String dbPrefix) {
        return Optional.empty();
    }

    /**
     * 批量更新节点。
     *
     * @param nodeDTOList 节点列表
     */
    @Override
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void editNodeBatch(final List<NodeDTO> nodeDTOList) {
        checkDatabaseEquals(nodeDTOList);

        // 按标签组合分组处理，提高批处理效率
        Map<String, List<NodeDTO>> labelGroups = nodeDTOList.stream()
                .collect(Collectors.groupingBy(CypherUtils::getLabelSignature));

        for (Map.Entry<String, List<NodeDTO>> entry : labelGroups.entrySet()) {
            List<NodeDTO> nodes = entry.getValue();

            for (NodeDTO node : nodes) {
                editNode(node);
            }
        }
    }

    /**
     * 批量导入节点和关系。
     *
     * @param graphDTO 图谱信息
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void importNodeAndRelationshipBatch(final GraphDTO graphDTO) {
        // 批量创建节点
        createNodeBatch(graphDTO.getNodeDTOList());

        // 批量创建关系 - 分批处理避免数据库压力过大
        if (graphDTO.getRelationshipDTOList() != null && !graphDTO.getRelationshipDTOList().isEmpty()) {
            String load = """
                        SET search_path = ag_catalog, "$user", ontology;
                    """;
            jdbcTemplate.execute(load);

            String database = AgeSql.graphName(graphDTO.getDbPrefix(), graphDTO.getDatabaseId());
            List<RelationshipDTO> relationshipList = graphDTO.getRelationshipDTOList();
            int totalSize = relationshipList.size();

            // 分批处理关系，减少单次事务压力
            for (int i = 0; i < totalSize; i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, totalSize);
                List<RelationshipDTO> batch = relationshipList.subList(i, endIndex);

                // 批量执行当前批次的关系创建
                createRelationshipBatchInternal(batch, database);
            }
        }
    }

    /**
     * 批量创建关系.
     *
     * @param batch    关系批次
     * @param database 数据库名称
     */
    private void createRelationshipBatchInternal(final List<RelationshipDTO> batch, final String database) {
        for (RelationshipDTO relationshipDTO : batch) {
            String cypher = CypherUtils.generateCreateRelationshipCypher(relationshipDTO.getName(),
                    relationshipDTO.getId(), relationshipDTO.getSourceId(), relationshipDTO.getTargetId(),
                    relationshipDTO.getProperties());
            String sql = AgeSql.cypherSql(database, cypher, List.of("n"));

            jdbcTemplate.queryForList(sql);
        }
    }

    /**
     * 检查节点是否存在.
     *
     * @param nodeId   节点ID
     * @param database 数据库名称
     */
    private void checkNodeExist(final Long nodeId, final String database) {
        String load = """
                    SET search_path = ag_catalog, "$user", ontology;
                """;
        jdbcTemplate.execute(load);
        String sql = AgeSql.cypherSql(database,
                "MATCH (n {_id: " + AgeSql.cypherInteger(nodeId) + "}) RETURN n", List.of("cnt"));

        Map<String, Object> cnt = jdbcTemplate.queryForMap(sql);

        Optional.ofNullable(cnt.get("cnt"))
                .map(n -> {
                    try {
                        Map<String, Object> map = objectMapper.readValue(((PGobject) n).getValue(), Map.class);
                        return map.get("label");
                    } catch (JsonProcessingException e) {
                        throw new BusinessException(ResultEnum.PARAM_IS_INVALID);
                    }
                })
                .map(Object::toString)
                .orElseThrow(() -> new ValidateException(ResultEnum.NODE_ERROR.getCode(), I18nUtils.getMessage(I18nConstant.NODE_NOT_EXIST)));
    }

    /**
     * 执行图查询。
     *
     * @param cypher 图查询语句
     * @param databaseId 图库编号
     * @param dbPrefix 图库名称前缀
     * @return 查询结果集合
     */
    @Override
    public Collection<Map<String, Object>> query(final String cypher, final Long databaseId, final String dbPrefix) {
        try {
            String load = """
                        SET search_path = ag_catalog, "$user", ontology;
                    """;
            jdbcTemplate.execute(load);

            // AGE 的 query 参数是 cstring，统一通过安全字面量编码；返回列描述符只来自词法解析。
            List<String> columns = AgeSql.parseReturnColumns(cypher);
            String sql = AgeSql.cypherSql(AgeSql.graphName(dbPrefix, databaseId), cypher, columns);

            List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(sql);

            List<Map<String, Object>> adapted = new ArrayList<>(rawResults.size());
            for (Map<String, Object> record : rawResults) {
                adapted.add(convertRecord(record));
            }

            // 如果查询只返回节点，自动查询并添加节点间的关系
            return enrichWithRelationships(adapted, databaseId, dbPrefix);
        } catch (UncategorizedSQLException e) {
            throw storageFailure(ResultEnum.NODE_ERROR, "执行图查询", e);
        } catch (ValidateException e) {
            throw new ValidateException(e.getErrCode(), e.getErrMsg());
        } catch (Exception e) {
            throw storageFailure(ResultEnum.GRAPH_ERROR, "执行图查询", e);
        }
    }

    /**
     * 通用结果行转换：将 AGE 查询结果转换为可序列化结构.
     *
     * @param record 结果行
     * @return 结果行
     */
    public Map<String, Object> convertRecord(final Map<String, Object> record) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            result.put(entry.getKey(), adaptValue(entry.getValue()));
        }
        return result;
    }

    /**
     * 通用值转换：将 AGE 返回的 PGobject 等适配为可序列化结构.
     *
     * @param value 待转换的值
     * @return 适配后的值
     */
    @SneakyThrows
    private Object adaptValue(final Object value) {
        if (value == null) {
            return null;
        }

        return switch (ValueType.of(value)) {
            case MAP -> adaptMap((Map<?, ?>) value);
            case PG_OBJECT -> adaptAgeValue((PGobject) value);
            case ITERABLE -> adaptIterable((Iterable<?>) value);
            default -> value;
        };
    }

    /**
     * 适配 AGE 的 PGobject 值.
     *
     * @param pgObj PGobject
     * @return 适配后的值
     */
    private Object adaptAgeValue(final PGobject pgObj) {
        if (pgObj.getValue() == null) {
            return null;
        }

        String jsonStr = getString(pgObj);

        try {
            Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);

            if (parsed.containsKey("start_id") && parsed.containsKey("end_id")) {
                return adaptAgeRelationship(parsed);
            }

            if (parsed.containsKey("label") && parsed.containsKey("properties")
                    && !parsed.containsKey("start_id") && !parsed.containsKey("end_id")) {
                return adaptAgeNode(parsed);
            }

            if (parsed.containsKey("vertices") && parsed.containsKey("edges")) {
                return adaptAgePath(parsed);
            }

            return parsed;

        } catch (Exception e) {
            return jsonStr.replace("\\\\", "\\").replace("\\\"", "\"");
        }
    }

    /**
     * 获取 PGobject 的值.
     * @param pgObj PGobject
     * @return 值
     */
    private static String getString(final PGobject pgObj) {
        String jsonStr = pgObj.getValue();

        while (jsonStr.startsWith("\"\"") && jsonStr.endsWith("\"\"") && jsonStr.length() > 4) {
            jsonStr = jsonStr.substring(2, jsonStr.length() - 2);
        }
        if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"") && jsonStr.length() > 2) {
            String inner = jsonStr.substring(1, jsonStr.length() - 1);
            if (!inner.trim().startsWith("{") && !inner.trim().startsWith("[")) {
                jsonStr = inner;
            }
        }
        return jsonStr;
    }

    /**
     * 适配 AGE 节点.
     *
     * @param nodeMap AGE节点Map
     * @return 适配后的节点
     */
    private Map<String, Object> adaptAgeNode(final Map<String, Object> nodeMap) {
        Map<String, Object> properties = (Map<String, Object>) nodeMap.get("properties");
        String label = (String) nodeMap.get("label");
        Object identity = nodeMap.get("id");

        return Map.of(
                "identity", identity,
                "labels", List.of(label),
                "properties", properties != null ? properties : Map.of()
        );
    }

    /**
     * 适配 AGE 关系.
     *
     * @param relMap AGE关系Map
     * @return 适配后的关系
     */
    private Map<String, Object> adaptAgeRelationship(final Map<String, Object> relMap) {
        Map<String, Object> properties = (Map<String, Object>) relMap.get("properties");
        String type = (String) relMap.get("label");
        Object identity = relMap.get("id");
        Object startId = relMap.get("start_id");
        Object endId = relMap.get("end_id");

        // 构建返回的Map，确保字段顺序和格式
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identity", identity != null ? identity.toString() : "0");
        result.put("properties", properties != null ? properties : Map.of());
        result.put("end", endId != null ? endId.toString() : "");
        result.put("type", type != null ? type : "");
        result.put("start", startId != null ? startId.toString() : "");

        return result;
    }

    /**
     * 适配 AGE 路径.
     *
     * @param pathMap AGE路径Map
     * @return 适配后的路径
     */
    private Map<String, Object> adaptAgePath(final Map<String, Object> pathMap) {
        List<Object> vertices = (List<Object>) pathMap.get("vertices");
        List<Object> edges = (List<Object>) pathMap.get("edges");

        List<Object> adaptedNodes = new ArrayList<>();
        if (vertices != null) {
            for (Object vertex : vertices) {
                adaptedNodes.add(adaptValue(vertex));
            }
        }

        List<Object> adaptedRelationships = new ArrayList<>();
        if (edges != null) {
            for (Object edge : edges) {
                adaptedRelationships.add(adaptValue(edge));
            }
        }

        return Map.of("nodes", adaptedNodes, "relationships", adaptedRelationships);
    }

    /**
     * 转换映射中的 AGE 属性值。
     *
     * @param m 原属性映射
     * @return 转换后的属性映射
     */
    private Map<String, Object> adaptMap(final Map<?, ?> m) {
        Map<String, Object> adapted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            adapted.put(String.valueOf(e.getKey()), adaptValue(e.getValue()));
        }
        return adapted;
    }

    /**
     * 转换可迭代对象中的 AGE 属性值。
     *
     * @param it 原属性集合
     * @return 转换后的属性列表
     */
    private List<Object> adaptIterable(final Iterable<?> it) {
        List<Object> list = new ArrayList<>();
        for (Object o : it) {
            list.add(adaptValue(o));
        }
        return list;
    }

    /**
     * 补充节点间的关系.
     * 如果查询结果只包含节点，自动查询这些节点之间的关系并添加到结果中
     *
     * @param results    原始查询结果
     * @param databaseId 数据库ID
     * @param dbPrefix   数据库前缀
     * @return 补充了关系的结果
     */
    private Collection<Map<String, Object>> enrichWithRelationships(
            final List<Map<String, Object>> results,
            final Long databaseId,
            final String dbPrefix) {

        // 检查是否需要补充关系（仅当结果只包含节点时）
        if (!shouldEnrichRelationships(results)) {
            return results;
        }

        // 收集所有节点的ID
        Set<Object> nodeIds = collectNodeIds(results);
        if (nodeIds.isEmpty()) {
            return results;
        }

        // 查询这些节点之间的关系
        List<Map<String, Object>> relationships = queryRelationshipsBetweenNodes(nodeIds, databaseId, dbPrefix);

        // 构建增强的结果
        Map<String, Object> enrichedResult = new LinkedHashMap<>();
        enrichedResult.put("nodes", extractNodes(results));
        enrichedResult.put("relationships", relationships);

        return List.of(enrichedResult);
    }

    /**
     * 判断是否需要补充关系.
     *
     * @param results 查询结果
     * @return boolean
     */
    private boolean shouldEnrichRelationships(final List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return false;
        }

        // 检查结果是否只包含节点（没有关系、路径等）
        for (Map<String, Object> record : results) {
            for (Object value : record.values()) {
                if (value instanceof Map<?, ?> m) {
                    // 检查是否是节点结构（包含labels字段）
                    if (!m.containsKey("labels")) {
                        return false;
                    }
                } else if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 收集所有节点的ID.
     *
     * @param results 查询结果
     * @return Object
     */
    private Set<Object> collectNodeIds(final List<Map<String, Object>> results) {
        Set<Object> nodeIds = new HashSet<>();
        for (Map<String, Object> record : results) {
            for (Object value : record.values()) {
                if (value instanceof Map<?, ?> m && m.containsKey("properties")) {
                    // 获取节点的id属性
                    Object props = m.get("properties");
                    if (props instanceof Map<?, ?> properties) {
                        Object id = properties.get("_id");
                        if (id != null) {
                            nodeIds.add(id);
                        }
                    }
                }
            }
        }
        return nodeIds;
    }

    /**
     * 查询指定节点之间的所有关系.
     *
     * @param nodeIds    节点ID
     * @param databaseId 数据库ID
     * @param dbPrefix   数据库前缀
     * @return List
     */
    private List<Map<String, Object>> queryRelationshipsBetweenNodes(final Set<Object> nodeIds, final Long databaseId, final String dbPrefix) {
        if (nodeIds.isEmpty()) {
            return List.of();
        }

        Set<Long> relationshipIds = new HashSet<>();

        if (GraphTypeEnum.ENTITY.name().equals(dbPrefix.toUpperCase())) {
            // 查询实体关系id
            relationshipIds = entityRelationshipDao.lambdaQuery()
                    .in(EntityRelationship::getSourceNodeId, nodeIds)
                    .in(EntityRelationship::getTargetNodeId, nodeIds)
                    .list()
                    .stream()
                    .map(EntityRelationship::getId)
                    .map(BigInteger::longValue)
                    .collect(Collectors.toSet());
        } else {
            relationshipIds = ontologyRelationshipDao.lambdaQuery()
                    .in(OntologyRelationship::getSourceNodeId, nodeIds)
                    .in(OntologyRelationship::getTargetNodeId, nodeIds)
                    .list()
                    .stream()
                    .map(OntologyRelationship::getId)
                    .map(BigInteger::longValue)
                    .collect(Collectors.toSet());
        }

        if (relationshipIds.isEmpty()) {
            log.info("未找到节点间的关系");
            return List.of();
        }

        // 分批查询
        int batchSize = 100;
        List<Long> relationshipIdList = new ArrayList<>(relationshipIds);
        List<Map<String, Object>> allRelationships = new ArrayList<>();

        String database = AgeSql.graphName(dbPrefix, databaseId);

        try {
            String load = """
                        SET search_path = ag_catalog, "$user", ontology;
                    """;
            jdbcTemplate.execute(load);

            // 分批处理
            for (int i = 0; i < relationshipIdList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, relationshipIdList.size());
                List<Long> batchIds = relationshipIdList.subList(i, end);

                // 构建当前批次的查询
                String cypher = "MATCH ()-[r]-() WHERE r._id IN "
                        + AgeSql.cypherIntegerList(batchIds) + " RETURN DISTINCT r";
                String sql = AgeSql.cypherSql(database, cypher, List.of("r"));

                List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(sql);

                // 转换并添加到结果集
                for (Map<String, Object> record : rawResults) {
                    Map<String, Object> converted = convertRecord(record);
                    Object rel = converted.get("r");
                    if (rel != null) {
                        allRelationships.add((Map<String, Object>) rel);
                    }
                }
            }
            return allRelationships;

        } catch (Exception e) {
            throw storageFailure(ResultEnum.RELATIONSHIP_ERROR, "查询节点间关系", e);
        }
    }

    /**
     * 从结果中提取所有节点.
     *
     * @param results 查询结果
     * @return List
     */
    private List<Map<String, Object>> extractNodes(final List<Map<String, Object>> results) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> record : results) {
            for (Object value : record.values()) {
                if (value instanceof Map<?, ?> m && m.containsKey("labels")) {
                    nodes.add((Map<String, Object>) m);
                }
            }
        }
        return nodes;
    }

    /**
     * 检查 label 是否还在被使用.
     *
     * @param label    label
     * @param database 数据库
     * @return boolean
     */
    private boolean isLabelStillInUse(final String label, final String database) {
        try {
            String escapedLabel = CypherUtils.quoteIdentifier(label);
            String cypher = "MATCH (n:" + escapedLabel + ") RETURN count(n) as cnt "
                    + "UNION ALL MATCH ()-[r:" + escapedLabel + "]->() RETURN count(r) as cnt";
            String sql = AgeSql.cypherSql(database, cypher, List.of("cnt"));

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> result : results) {
                String cntStr = ((PGobject) result.get("cnt")).getValue();
                int count = Integer.parseInt(cntStr);
                if (count > 0) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logStorageFailure("检查 AGE label 使用状态", e);
            return false;
        }
    }

    /**
     * 删除 label.
     *
     * @param label    label
     * @param database 数据库
     */
    private void dropLabel(final String label, final String database) {
        try {
            AgeSql.executePrepared(jdbcTemplate, "SELECT ag_catalog.drop_label(?, ?)", database, label);
        } catch (Exception e) {
            throw storageFailure(ResultEnum.GRAPH_ERROR, "删除 AGE label", e);
        }
    }

    /** 数据库错误只记录异常类型和 PostgreSQL 数字/状态码，避免 SQL 与图属性进入日志。 */
    private RuntimeException storageFailure(
            final ResultEnum error, final String operation, final Throwable failure) {
        logStorageFailure(operation, failure);
        if (hasAgeLoadError(failure)) {
            return new ClientException(ResultEnum.AGE_ERROR);
        }
        return new BusinessException(error);
    }

    private void logStorageFailure(final String operation, final Throwable failure) {
        SQLException sqlException = findSqlException(failure);
        Throwable rootCause = failure;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String sqlState = sqlException == null ? "n/a" : safeSqlState(sqlException.getSQLState());
        int vendorCode = sqlException == null ? 0 : sqlException.getErrorCode();
        log.error("{} failed; exceptionType={}, rootCauseType={}, sqlState={}, vendorCode={}",
                operation, failure.getClass().getSimpleName(), rootCause.getClass().getSimpleName(),
                sqlState, vendorCode);
    }

    private static SQLException findSqlException(final Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String safeSqlState(final String sqlState) {
        return sqlState != null && sqlState.matches("[A-Za-z0-9]{5}") ? sqlState : "unknown";
    }

    private static boolean hasAgeLoadError(final Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(AGE_LOAD_ERROR_MSG)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
