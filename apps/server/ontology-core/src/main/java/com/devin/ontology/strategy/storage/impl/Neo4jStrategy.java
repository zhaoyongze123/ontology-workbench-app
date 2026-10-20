package com.devin.ontology.strategy.storage.impl;

import com.devin.ontology.strategy.storage.CypherUtils;
import com.devin.ontology.domain.dto.GraphDTO;
import com.devin.ontology.domain.dto.NodeDTO;
import com.devin.ontology.domain.dto.RelationshipDTO;
import com.devin.ontology.strategy.storage.ChartStorageStrategyAbstract;
import com.devin.ontology.common.constant.I18nConstant;
import com.devin.ontology.common.exception.BusinessException;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.common.utils.i18n.I18nUtils;
import com.devin.ontology.common.utils.r.ResultEnum;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.Path;

/**
 * 2025/6/5 10:28.
 *
 * <p>
 * Neo4j策略类
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "graph.database.type", havingValue = "neo4j")
public class Neo4jStrategy extends ChartStorageStrategyAbstract {

    private final Neo4jClient neo4jClient;

    private final Driver neo4jDriver;

    public Neo4jStrategy(final Neo4jClient neo4jClient, final Driver neo4jDriver) {
        this.neo4jClient = neo4jClient;
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public void dropDatabase(final Collection<Long> ids, final String dbPrefix) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 删除数据库
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase("system"))) {
            session.executeWrite(tx -> {
                ids.forEach(id -> {
                    tx.run("DROP DATABASE $dbName IF EXISTS", Map.of(
                            "dbName", generateDatabase(dbPrefix, id)
                    ));
                });
                return null;
            });
        } catch (Exception e) {
            log.error("删除图库失败 exceptionType={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 创建数据库.
     *
     * @param id 数据库id
     */
    @Override
    public void createDatabase(final String dbPrefix, final Long id) {
        String cypher = "CREATE DATABASE " + dbPrefix + id;

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher);
                return null;
            });
        } catch (Exception e) {
            log.error("创建图库失败 exceptionType={}", e.getClass().getSimpleName());
            throw new BusinessException(ResultEnum.GRAPH_ERROR, "创建图库异常");
        }
    }

    @Override
    public void createNode(final NodeDTO nodeDTO) {
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(generateDatabase(nodeDTO.getDbPrefix(), nodeDTO.getDatabaseId())))) {
            session.executeWrite(tx -> {
                tx.run(CypherUtils.generateCreateNodeCypher(nodeDTO), Map.of(
                        "id", nodeDTO.getId(),
                        "properties", nodeDTO.getProperties()
                ));
                return null;
            });
        } catch (Exception e) {
            log.error("创建节点失败 exceptionType={}", e.getClass().getSimpleName());
            throw new BusinessException(ResultEnum.NODE_ERROR, "创建节点异常");
        }
    }

    @Override
    public void createNodeBatch(final List<NodeDTO> nodeDTOList) {
        checkDatabaseEquals(nodeDTOList);

        // 简单按顺序创建，复用单条创建的安全标签处理逻辑
        for (NodeDTO nodeDTO : nodeDTOList) {
            createNode(nodeDTO);
        }
    }

    @Override
    public void deleteNodeBatch(@NotEmpty final List<Long> ids, final Long databaseId, final String dbPrefix) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        String cypher = "MATCH (n) WHERE n._id IN $ids DETACH DELETE n";

        neo4jClient.query(cypher)
                .in(generateDatabase(dbPrefix, databaseId))
                .bind(ids).to("ids")
                .run();
    }

    @Override
    public void editNode(final NodeDTO nodeDTO) {
        // 判断节点是否是原节点label
        String cypher = findNodeById(nodeDTO.getId(), nodeDTO.getDatabaseId(), nodeDTO.getDbPrefix())
                .map(node -> {
                    List<String> nodes = (List<String>) node.get("labels");
                    String oldLabelStr = CypherUtils.generateLabelStr(nodes);
                    String newLabelStr = CypherUtils.generateLabelStr(nodeDTO.getLabels());

                    if (oldLabelStr.equals(newLabelStr)) {
                        return "MERGE (node" + oldLabelStr + " {_id: $id}) "
                                + "SET node = $properties, node._id = $id "
                                + "RETURN node._id as id";
                    } else {
                        return "MERGE (node" + oldLabelStr + " {_id: $id}) "
                                + "SET node = $properties, node._id = $id "
                                + "SET node" + newLabelStr + " "
                                + "REMOVE node" + oldLabelStr + " "
                                + "RETURN node._id as id";
                    }
                }).orElseThrow(() -> new BusinessException(ResultEnum.NODE_ERROR, I18nUtils.getMessage(I18nConstant.NODE_NOT_EXIST)));

        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(generateDatabase(nodeDTO.getDbPrefix(), nodeDTO.getDatabaseId())))) {
            session.executeWrite(tx -> {
                tx.run(cypher, Map.of(
                        "id", nodeDTO.getId(),
                        "properties", nodeDTO.getProperties()
                ));
                return null;
            });
        } catch (Exception e) {
            log.error("修改节点失败 exceptionType={}", e.getClass().getSimpleName());
            throw new BusinessException(ResultEnum.NODE_ERROR, "修改节点失败");
        }
    }

    @Override
    public void importNodeAndRelationshipBatch(final GraphDTO graphDTO) {
        String database = generateDatabase(graphDTO.getDbPrefix(), graphDTO.getDatabaseId());
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                // 创建节点
                graphDTO.getNodeDTOList().forEach(nodeDTO -> {
                    tx.run(CypherUtils.generateCreateNodeCypher(nodeDTO), Map.of(
                            "id", nodeDTO.getId(),
                            "properties", nodeDTO.getProperties()
                    ));
                });

                // 创建关系
                graphDTO.getRelationshipDTOList().forEach(relationshipDTO -> {

                    checkNodeExist(relationshipDTO.getSourceId(), tx);
                    checkNodeExist(relationshipDTO.getTargetId(), tx);

                    tx.run(CypherUtils.generateCreateRelationshipCypher(relationshipDTO.getName()), Map.of(
                            "id", relationshipDTO.getId(),
                            "sourceId", relationshipDTO.getSourceId(),
                            "targetId", relationshipDTO.getTargetId(),
                            "properties", relationshipDTO.getProperties()
                    ));

                });

                return null;
            });
        } catch (Exception e) {
            // 删除导入的数据库
            log.error("图数据导入失败 exceptionType={}", e.getClass().getSimpleName());
            dropDatabase(Collections.singleton(graphDTO.getDatabaseId()), graphDTO.getDbPrefix());
            // 抛出异常，触发PG回滚机制
            throw new BusinessException(ResultEnum.GRAPH_ERROR.getCode(), "图数据导入失败");
        }
    }

    @Override
    public void editNodeBatch(final List<NodeDTO> nodeDTOList) {
        if (nodeDTOList == null || nodeDTOList.isEmpty()) {
            return;
        }

        // 校验同库
        Long databaseId = nodeDTOList.get(0).getDatabaseId();

        if (nodeDTOList.stream().anyMatch(n -> !Objects.equals(n.getDatabaseId(), databaseId))) {
            throw new ValidateException("同一批次的节点必须属于同一个数据库");
        }

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

    @Override
    public Optional<Map<String, Object>> findNodeById(final Long id, final Long databaseId, final String dbPrefix) {
        return neo4jClient.query("MATCH (n) WHERE n._id = $id RETURN n, labels(n) as labels, n._id as id")
                .in(generateDatabase(dbPrefix, databaseId))
                .bind(id).to("id")
                .fetch()
                .one()
                .map(this::convertNodeToMap);
    }

    // ========= 关系操作 ========
    @Override
    public void createRelationship(final RelationshipDTO relationshipDTO) {

        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(generateDatabase(relationshipDTO.getDbPrefix(), relationshipDTO.getDatabaseId())))) {
            session.executeWrite(tx -> {
                // 校验节点是否存在
                checkNodeExist(relationshipDTO.getSourceId(), tx);
                checkNodeExist(relationshipDTO.getTargetId(), tx);

                tx.run(CypherUtils.generateCreateRelationshipCypher(relationshipDTO.getName()), Map.of(
                        "id", relationshipDTO.getId(),
                        "sourceId", relationshipDTO.getSourceId(),
                        "targetId", relationshipDTO.getTargetId(),
                        "properties", relationshipDTO.getProperties())
                );

                return null;
            });
        } catch (Exception e) {
            throw new BusinessException(ResultEnum.RELATIONSHIP_ERROR, "创建关系异常");
        }
    }

    @Override
    public void deleteRelationshipBatch(@NotEmpty final List<Long> ids, final Long databaseId, final String dbPrefix) {
//        useDatabase(databaseId);
        if (ids == null || ids.isEmpty()) {
            return;
        }

        String cypher = "MATCH ()-[r]->() WHERE r._id IN $ids DELETE r";

        neo4jClient.query(cypher)
                .in(generateDatabase(dbPrefix, databaseId))
                .bind(ids).to("ids")
                .run();
    }

    @Override
    public void editRelationship(final RelationshipDTO relationshipDTO) {
        String cypher = "MATCH ()-[r]->() WHERE r._id = $id SET r = $properties SET r._id = $id RETURN r._id as id";

        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(generateDatabase(relationshipDTO.getDbPrefix(), relationshipDTO.getDatabaseId())))) {
            session.run(cypher, Map.of(
                    "id", relationshipDTO.getId(),
                    "properties", relationshipDTO.getProperties()
            ));
        } catch (Exception e) {
            throw new BusinessException(ResultEnum.RELATIONSHIP_ERROR, "修改关系异常");
        }
    }

    @Override
    public Optional<Map<String, Object>> findRelationshipById(final Long id, final Long databaseId, final String dbPrefix) {
        String cypher = "MATCH (a)-[r]->(b) WHERE r._id = $id "
                + "RETURN r._id as id, type(r) as type, r as properties, "
                + "a._id as sourceId, b._id as targetId ";
        return neo4jClient.query(cypher)
                .in(generateDatabase(dbPrefix, databaseId))
                .bind(id).to("id")
                .fetch()
                .one();
    }

    @Override
    public Collection<Map<String, Object>> query(final String cypher, final Long databaseId, final String dbPrefix) {
        Collection<Map<String, Object>> raw = neo4jClient.query(cypher)
                .in(generateDatabase(dbPrefix, databaseId))
                .fetch()
                .all();

        List<Map<String, Object>> adapted = new ArrayList<>(raw.size());
        for (Map<String, Object> record : raw) {
            adapted.add(convertRecord(record));
        }

        // 如果查询只返回节点，自动查询并添加节点间的关系
        return enrichWithRelationships(adapted, cypher, databaseId, dbPrefix);
    }

    /**
     * 从数据库中获取所有已知的标签.
     *
     * @return 标签列表
     */
    private List<String> getAllLabels() {
        Collection<String> labels = neo4jClient.query("CALL db.labels() YIELD label").fetchAs(String.class).all();
        return List.copyOf(labels);
    }

    private Map<String, Object> convertNodeToMap(final Map<String, Object> record) {
        Node node = (Node) record.get("n");
        List<String> labels = (List<String>) record.get("labels");
        Long id = (Long) record.get("id");

        return Map.of("id", id, "labels", labels, "properties", node.asMap());
    }

    /**
     * 通用结果行转换：将 Node/Relationship/Path/List/Map/Value 转换为可序列化结构.
     *
     * @param record 结果行
     * @return 结果行
     */
    private Map<String, Object> convertRecord(final Map<String, Object> record) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            result.put(entry.getKey(), adaptValue(entry.getValue()));
        }
        return result;
    }

    /**
     * 通用值转换：将 Node/Relationship/Path/List/Map/Value 适配为可序列化结构.
     *
     * @param value 待转换的值
     * @return 适配后的值
     */
    private Object adaptValue(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Value v) {
            return adaptNeo4jValue(v);
        }
        if (value instanceof Node node) {
            return adaptNode(node);
        }
        if (value instanceof Relationship rel) {
            return adaptRelationship(rel);
        }
        if (value instanceof Path path) {
            return adaptPath(path);
        }
        if (value instanceof Map<?, ?> m) {
            return adaptMap(m);
        }
        if (value instanceof Iterable<?> it) {
            return adaptIterable(it);
        }
        // 标量
        return value;
    }

    /**
     * 适配 Node.
     *
     * @param node Node
     * @return 适配后的 Node
     */
    private Map<String, Object> adaptNode(final Node node) {
        return Map.of(
                "identity", node.elementId(),
                "labels", node.labels(),
                "properties", node.asMap()
        );
    }

    /**
     * 适配 Relationship.
     *
     * @param rel Relationship
     * @return 适配后的 Relationship
     */
    private Map<String, Object> adaptRelationship(final Relationship rel) {
        return Map.of(
                "identity", rel.elementId(),
                "type", rel.type(),
                "start", rel.startNodeElementId(),
                "end", rel.endNodeElementId(),
                "properties", rel.asMap()
        );
    }

    private Map<String, Object> adaptPath(final Path path) {
        List<Object> nodes = new ArrayList<>();
        path.nodes().forEach(n -> nodes.add(adaptValue(n)));
        List<Object> relationships = new ArrayList<>();
        path.relationships().forEach(r -> relationships.add(adaptValue(r)));
        return Map.of("nodes", nodes, "relationships", relationships);
    }

    private Map<String, Object> adaptMap(final Map<?, ?> m) {
        Map<String, Object> adapted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            adapted.put(String.valueOf(e.getKey()), adaptValue(e.getValue()));
        }
        return adapted;
    }

    private List<Object> adaptIterable(final Iterable<?> it) {
        List<Object> list = new ArrayList<>();
        for (Object o : it) {
            list.add(adaptValue(o));
        }
        return list;
    }

    /**
     * Neo4j Value 转换为可序列化结构.
     *
     * @param v Neo4j Value
     * @return 可序列化结构
     */
    private Object adaptNeo4jValue(final Value v) {
        if (v.isNull()) {
            return null;
        }
        // 基于类型名称做统一分发，降低分支复杂度
        String typeName = v.type().name();
        return switch (typeName) {
            case "NODE" -> adaptValue(v.asNode());
            case "RELATIONSHIP" -> adaptValue(v.asRelationship());
            case "PATH" -> adaptValue(v.asPath());
            case "LIST" -> adaptListValue(v);
            case "MAP" -> adaptMapValue(v);
            default -> adaptScalarValue(v);
        };
    }

    private Object adaptListValue(final Value v) {
        List<Object> list = new ArrayList<>();
        v.asList().forEach(item -> list.add(adaptValue(item)));
        return list;
    }

    private Map<String, Object> adaptMapValue(final Value v) {
        Map<String, Object> map = new LinkedHashMap<>();
        v.asMap().forEach((k, val) -> map.put(k, adaptValue(val)));
        return map;
    }

    private Object adaptScalarValue(final Value v) {
        try {
            return v.asObject();
        } catch (Exception e) {
            return v.toString();
        }
    }

    /**
     * 补充节点间的关系.
     * 如果查询结果只包含节点，自动查询这些节点之间的关系并添加到结果中
     *
     * @param results        原始查询结果
     * @param originalCypher 原始查询语句
     * @param databaseId     数据库ID
     * @param dbPrefix       数据库前缀
     * @return 补充了关系的结果
     */
    private Collection<Map<String, Object>> enrichWithRelationships(
            final List<Map<String, Object>> results,
            final String originalCypher,
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

        // 增强标志
        // enrichedResult.put("_enriched", true);

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
                        Object id = properties.get("id");
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

        String cypher = "MATCH (n)-[r]-(m) "
                + "WHERE n._id IN $nodeIds AND m._id IN $nodeIds "
                + "RETURN DISTINCT r";

        Collection<Map<String, Object>> raw = neo4jClient.query(cypher)
                .in(generateDatabase(dbPrefix, databaseId))
                .bind(new ArrayList<>(nodeIds)).to("nodeIds")
                .fetch()
                .all();

        List<Map<String, Object>> relationships = new ArrayList<>();
        for (Map<String, Object> record : raw) {
            Map<String, Object> converted = convertRecord(record);
            Object rel = converted.get("r");
            if (rel != null) {
                relationships.add((Map<String, Object>) rel);
            }
        }

        return relationships;
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
     * 检查节点是否存在.
     *
     * @param nodeId 节点ID
     * @param tx     neo4j会话
     */
    private void checkNodeExist(final Long nodeId, final TransactionContext tx) {
        String cypher = "MATCH (n {_id: $id}) RETURN count(n) as cnt";

        Result result = tx.run(cypher, Map.of("id", nodeId));
        // 检查源节点和目标节点是否存在
        if (result.single().get("cnt").asLong() == 0) {
            throw new ValidateException(ResultEnum.NODE_ERROR.getCode(), I18nUtils.getMessage(I18nConstant.NODE_NOT_EXIST));
        }
    }
}
