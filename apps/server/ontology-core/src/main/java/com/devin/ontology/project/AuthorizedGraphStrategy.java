package com.devin.ontology.project;

import com.alibaba.fastjson2.JSON;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.utils.r.PageResult;
import com.devin.ontology.dao.OrganizationDao;
import com.devin.ontology.domain.entity.Graph;
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
import com.devin.ontology.strategy.graph.GraphStrategy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 在原策略周围统一校验资源归属、项目能力和审计，确保校验与策略操作共享事务。
 */
public final class AuthorizedGraphStrategy implements GraphStrategy {
    private final GraphStrategy delegate;
    private final JdbcTemplate jdbc;
    private final ProjectAccessService access;
    private final OrganizationDao organizationDao;
    private final TransactionTemplate transactions;
    private final com.devin.ontology.strategy.graph.impl.OntologyGraphStrategy ontologyDelegate;

    public AuthorizedGraphStrategy(final GraphStrategy delegate, final JdbcTemplate jdbc,
                                   final ProjectAccessService access, final OrganizationDao organizationDao,
                                   final TransactionTemplate transactions,
                                   final com.devin.ontology.strategy.graph.impl.OntologyGraphStrategy ontologyDelegate) {
        this.delegate = delegate;
        this.jdbc = jdbc;
        this.access = access;
        this.organizationDao = organizationDao;
        this.transactions = transactions;
        this.ontologyDelegate = ontologyDelegate;
    }

    @Override public GraphTypeEnum getGraphType() { return delegate.getGraphType(); }

    @Override
    public GraphVO saveGraph(final GraphSaveVO vo) {
        return tx(() -> {
            requireType(vo.getGraphType());
            access.requireProject(vo.getOrganizationId(), ProjectCapability.EDIT);
            if (getGraphType() == GraphTypeEnum.ENTITY) {
                Graph parent = graph(vo.getLinkGraphId());
                if (!GraphTypeEnum.ONTOLOGY.name().equals(parent.getType()) || !parent.getOrganizationId().equals(vo.getOrganizationId())) {
                    throw denied();
                }
            } else if (vo.getLinkGraphId() != null) {
                throw denied();
            }
            GraphVO saved = delegate.saveGraph(vo);
            audit("PROJECT_GRAPH_CREATED", saved.getOrganizationId(), "EDIT");
            return saved;
        });
    }

    @Override
    public void updateGraph(final GraphUpdateVO vo) {
        tx(() -> {
            Graph original = strategyGraph(vo.getId());
            requireType(vo.getGraphType());
            if (!original.getOrganizationId().equals(vo.getOrganizationId())
                    || !Objects.equals(original.getLinkGraphId(), vo.getLinkGraphId())) {
                throw denied();
            }
            access.requireProject(original.getOrganizationId(), ProjectCapability.EDIT);
            delegate.updateGraph(vo);
            audit("PROJECT_GRAPH_UPDATED", original.getOrganizationId(), "EDIT");
            return null;
        });
    }

    @Override
    public void saveNode(final NodeSaveVO vo) {
        tx(() -> {
            Graph graph = strategyGraph(vo.getGraphId());
            requireType(vo.getGraphType());
            access.requireProject(graph.getOrganizationId(), ProjectCapability.EDIT);
            if (getGraphType() == GraphTypeEnum.ENTITY) requireOntologyLink(graph, vo.getLinkOntologyId());
            delegate.saveNode(vo);
            audit("PROJECT_NODE_CREATED", graph.getOrganizationId(), "EDIT");
            return null;
        });
    }

    @Override
    public void updateNode(final NodeUpdateVO vo) {
        tx(() -> {
            BigInteger actualGraphId = nodeGraphId(vo.getId());
            Graph graph = strategyGraph(actualGraphId);
            requireType(vo.getGraphType());
            requireDeclaredGraph(vo.getGraphId(), actualGraphId);
            access.requireProject(graph.getOrganizationId(), ProjectCapability.EDIT);
            if (getGraphType() == GraphTypeEnum.ENTITY) requireOntologyLink(graph, vo.getLinkOntologyId());
            delegate.updateNode(vo);
            audit("PROJECT_NODE_UPDATED", graph.getOrganizationId(), "EDIT");
            return null;
        });
    }

    @Override
    public void deleteNode(final NodeDeleteVO vo) {
        tx(() -> {
            Graph graph = strategyGraph(vo.getGraphId());
            requireType(vo.getGraphType());
            requireIdsInGraph(vo.getIdList(), vo.getGraphId(), false);
            access.requireProject(graph.getOrganizationId(), ProjectCapability.EDIT);
            delegate.deleteNode(vo);
            audit("PROJECT_NODE_DELETED", graph.getOrganizationId(), "EDIT");
            return null;
        });
    }

    @Override
    public PageResult<NodeVO> pageNode(final NodeQueryVO vo) {
        return tx(() -> {
            Graph graph = strategyGraph(vo.getGraphId());
            requireType(vo.getGraphType());
            access.requireProject(graph.getOrganizationId(), ProjectCapability.READ);
            return delegate.pageNode(vo);
        });
    }

    @Override
    public void saveRelationship(final RelationshipSaveVO vo) {
        tx(() -> {
            Graph graph = strategyGraph(vo.getGraphId());
            requireType(vo.getGraphType());
            access.requireProject(graph.getOrganizationId(), ProjectCapability.EDIT);
            requireNodesInGraph(java.util.Arrays.asList(vo.getSourceNodeId(), vo.getTargetNodeId()), graph.getId());
            if (getGraphType() == GraphTypeEnum.ENTITY) requireRelationshipLink(graph, vo.getLinkOntologyRelationshipId());
            delegate.saveRelationship(vo);
            audit("PROJECT_RELATIONSHIP_CREATED", graph.getOrganizationId(), "EDIT");
            return null;
        });
    }

    @Override
    public void updateRelationship(final RelationshipUpdateVO vo) {
        tx(() -> {
            BigInteger actualGraphId = relationshipGraphId(vo.getId());
            Graph graph = strategyGraph(actualGraphId);
            requireType(vo.getGraphType());
            requireDeclaredGraph(vo.getGraphId(), actualGraphId);
            access.requireProject(graph.getOrganizationId(), ProjectCapability.EDIT);
            requireNodesInGraph(java.util.Arrays.asList(vo.getSourceNodeId(), vo.getTargetNodeId()), graph.getId());
            if (getGraphType() == GraphTypeEnum.ENTITY) requireRelationshipLink(graph, vo.getLinkOntologyRelationshipId());
            delegate.updateRelationship(vo);
            audit("PROJECT_RELATIONSHIP_UPDATED", graph.getOrganizationId(), "EDIT");
            return null;
        });
    }

    @Override
    public PageResult<RelationshipVO> pageRelationship(final RelationshipQueryVO vo) {
        return tx(() -> {
            Graph graph = strategyGraph(vo.getGraphId());
            requireType(vo.getGraphType());
            access.requireProject(graph.getOrganizationId(), ProjectCapability.READ);
            return delegate.pageRelationship(vo);
        });
    }

    @Override
    public void deleteRelationship(final RelationshipDeleteVO vo) {
        tx(() -> {
            Graph graph = strategyGraph(vo.getGraphId());
            requireType(vo.getGraphType());
            requireIdsInGraph(vo.getIdList(), vo.getGraphId(), true);
            access.requireProject(graph.getOrganizationId(), ProjectCapability.EDIT);
            delegate.deleteRelationship(vo);
            audit("PROJECT_RELATIONSHIP_DELETED", graph.getOrganizationId(), "EDIT");
            return null;
        });
    }

    @Override
    public PageResult<GraphVO> pageGraph(final GraphQueryVO vo) {
        return tx(() -> {
            requireType(vo.getGraphType());
            access.requireProject(vo.getOrganizationId(), ProjectCapability.READ);
            return delegate.pageGraph(vo);
        });
    }

    @Override
    public void deleteGraph(final GraphVO requested) {
        tx(() -> {
            Graph actual = strategyGraph(requested.getId());
            if (requested.getType() != null && !actual.getType().equals(requested.getType())) throw denied();
            if (requested.getOrganizationId() != null && !actual.getOrganizationId().equals(requested.getOrganizationId())) throw denied();
            access.requireProject(actual.getOrganizationId(), ProjectCapability.DELETE_GRAPH);
            GraphVO trusted = new GraphVO();
            trusted.setId(actual.getId());
            trusted.setType(actual.getType());
            trusted.setOrganizationId(actual.getOrganizationId());
            trusted.setLinkGraphId(actual.getLinkGraphId());
            delegate.deleteGraph(trusted);
            audit("PROJECT_GRAPH_DELETED", actual.getOrganizationId(), "DELETE_GRAPH");
            return null;
        });
    }

    @Override
    public GraphVO getGraphInfo(final BigInteger graphId) {
        return tx(() -> {
            Graph graph = strategyGraph(graphId);
            access.requireProject(graph.getOrganizationId(), ProjectCapability.READ);
            return delegate.getGraphInfo(graphId);
        });
    }

    @Override
    public NodeVO getNodeInfo(final BigInteger nodeId) {
        return tx(() -> {
            Graph graph = strategyGraph(nodeGraphId(nodeId));
            access.requireProject(graph.getOrganizationId(), ProjectCapability.READ);
            return delegate.getNodeInfo(nodeId);
        });
    }

    @Override
    public RelationshipVO getRelationshipInfo(final BigInteger relationshipId) {
        return tx(() -> {
            Graph graph = strategyGraph(relationshipGraphId(relationshipId));
            access.requireProject(graph.getOrganizationId(), ProjectCapability.READ);
            return delegate.getRelationshipInfo(relationshipId);
        });
    }

    @Override
    public JsonGraphInfo exportGraph(final BigInteger graphId) {
        return tx(() -> {
            Graph graph = strategyGraph(graphId);
            access.requireProject(graph.getOrganizationId(), ProjectCapability.READ);
            JsonGraphInfo exported = delegate.exportGraph(graphId);
            audit("PROJECT_GRAPH_EXPORTED", graph.getOrganizationId(), "READ");
            return exported;
        });
    }

    @Override
    public List<OptionalVO> getNodeOptional(final BigInteger graphId) {
        return tx(() -> {
            Graph graph = strategyGraph(graphId);
            access.requireProject(graph.getOrganizationId(), ProjectCapability.READ);
            return delegate.getNodeOptional(graphId);
        });
    }

    @Override
    public void importGraph(final String jsonData, final BigInteger organizationId) {
        tx(() -> {
            access.requireProject(organizationId, ProjectCapability.EDIT);
            JsonGraphInfo info;
            try {
                info = JSON.parseObject(jsonData, JsonGraphInfo.class);
            } catch (RuntimeException e) {
                throw denied();
            }
            validateImport(info);
            delegate.importGraph(jsonData, organizationId);
            audit("PROJECT_GRAPH_IMPORTED", organizationId, "EDIT");
            return null;
        });
    }

    @Override
    public List<OptionalVO> getRelationshipOptional(final BigInteger graphId) {
        return tx(() -> {
            Graph graph = strategyGraph(graphId);
            access.requireProject(graph.getOrganizationId(), ProjectCapability.READ);
            return delegate.getRelationshipOptional(graphId);
        });
    }

    /** 经授权读取实体图谱实际关联的本体节点。 */
    public List<NodeVO> getLinkOntologyByEntityGraphId(final BigInteger entityGraphId) {
        return tx(() -> {
            Graph entityGraph = graph(entityGraphId);
            if (!GraphTypeEnum.ENTITY.name().equals(entityGraph.getType())) throw denied();
            requireLinkedOntology(entityGraph);
            access.requireProject(entityGraph.getOrganizationId(), ProjectCapability.READ);
            return ontologyDelegate.getLinkOntologyByEntityGraphId(entityGraphId);
        });
    }

    /** 两个实体节点必须来自同一实体图谱，且该图谱链接到本项目本体图谱。 */
    public List<OptionalVO> getLinkOntologyRelationshipList(final BigInteger sourceNodeId,
                                                            final BigInteger targetNodeId) {
        return tx(() -> {
            BigInteger graphId = nodeGraphId(sourceNodeId);
            if (!graphId.equals(nodeGraphId(targetNodeId))) throw denied();
            Graph entityGraph = graph(graphId);
            if (!GraphTypeEnum.ENTITY.name().equals(entityGraph.getType())) throw denied();
            requireLinkedOntology(entityGraph);
            access.requireProject(entityGraph.getOrganizationId(), ProjectCapability.READ);
            return ontologyDelegate.getLinkOntologyRelationshipList(sourceNodeId, targetNodeId);
        });
    }

    private void validateImport(final JsonGraphInfo info) {
        if (info == null || info.getGraph() == null || info.getOntologyList() == null
                || containsNull(info.getOntologyList())
                || containsNull(info.getOntologyRelationshipList())
                || containsNull(info.getEntityList())
                || containsNull(info.getEntityRelationshipList())) throw denied();
        Graph importedGraph = info.getGraph();
        if (!getGraphType().name().equals(importedGraph.getType())) throw denied();
        Set<BigInteger> ontologyIds = ids(info.getOntologyList() == null ? List.of() : info.getOntologyList().stream().map(x -> x.getId()).toList());
        Set<BigInteger> ontologyRelationshipIds = ids(info.getOntologyRelationshipList() == null ? List.of() : info.getOntologyRelationshipList().stream().map(x -> x.getId()).toList());
        if (getGraphType() == GraphTypeEnum.ONTOLOGY) {
            for (var relationship : info.getOntologyRelationshipList() == null ? List.<com.devin.ontology.domain.entity.OntologyRelationship>of() : info.getOntologyRelationshipList()) {
                if (!ontologyIds.contains(relationship.getSourceNodeId()) || !ontologyIds.contains(relationship.getTargetNodeId())) throw denied();
            }
            return;
        }
        Graph embeddedOntology = importedGraph.getLinkOntologyGraph();
        if (embeddedOntology == null || !GraphTypeEnum.ONTOLOGY.name().equals(embeddedOntology.getType())
                || embeddedOntology.getId() == null || !embeddedOntology.getId().equals(importedGraph.getLinkGraphId())) {
            throw denied();
        }
        Set<BigInteger> entityIds = ids(info.getEntityList() == null ? List.of() : info.getEntityList().stream().map(x -> x.getId()).toList());
        for (var entity : info.getEntityList() == null ? List.<com.devin.ontology.domain.entity.Entity>of() : info.getEntityList()) {
            if (!ontologyIds.contains(entity.getOntologyId())) throw denied();
        }
        for (var relationship : info.getEntityRelationshipList() == null ? List.<com.devin.ontology.domain.entity.EntityRelationship>of() : info.getEntityRelationshipList()) {
            if (!entityIds.contains(relationship.getSourceNodeId()) || !entityIds.contains(relationship.getTargetNodeId())
                    || !ontologyRelationshipIds.contains(relationship.getOntologyRelationshipId())) throw denied();
        }
        for (var relationship : info.getOntologyRelationshipList() == null ? List.<com.devin.ontology.domain.entity.OntologyRelationship>of() : info.getOntologyRelationshipList()) {
            if (!ontologyIds.contains(relationship.getSourceNodeId()) || !ontologyIds.contains(relationship.getTargetNodeId())) throw denied();
        }
    }

    private Set<BigInteger> ids(final List<BigInteger> values) {
        if (values.stream().anyMatch(Objects::isNull) || values.size() != values.stream().distinct().count()) throw denied();
        return values.stream().collect(Collectors.toSet());
    }

    private boolean containsNull(final List<?> values) {
        return values != null && values.stream().anyMatch(Objects::isNull);
    }

    private void requireOntologyLink(final Graph entityGraph, final BigInteger ontologyId) {
        requireLinkedOntology(entityGraph);
        if (ontologyId == null || count("SELECT COUNT(*) FROM ontology.og_ontology WHERE id = ? AND graph_id = ?",
                ontologyId, entityGraph.getLinkGraphId()) != 1) throw denied();
    }

    private void requireRelationshipLink(final Graph entityGraph, final BigInteger ontologyRelationshipId) {
        requireLinkedOntology(entityGraph);
        if (ontologyRelationshipId == null || count(
                "SELECT COUNT(*) FROM ontology.og_ontology_relationship WHERE id = ? AND graph_id = ?",
                ontologyRelationshipId, entityGraph.getLinkGraphId()) != 1) throw denied();
    }

    private void requireLinkedOntology(final Graph entityGraph) {
        if (entityGraph.getLinkGraphId() == null) throw denied();
        Graph linked = graph(entityGraph.getLinkGraphId());
        if (!GraphTypeEnum.ONTOLOGY.name().equals(linked.getType()) || !linked.getOrganizationId().equals(entityGraph.getOrganizationId())) throw denied();
        // 防止旧数据或手工写入的实体跨链接读取其他本体项目的数据。
        Boolean invalidNodeLink = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM ontology.og_entity e "
                        + "LEFT JOIN ontology.og_ontology o ON o.id = e.ontology_id AND o.graph_id = ? "
                        + "WHERE e.graph_id = ? AND o.id IS NULL)",
                Boolean.class, entityGraph.getLinkGraphId(), entityGraph.getId());
        Boolean invalidRelationshipLink = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM ontology.og_entity_relationship r "
                        + "LEFT JOIN ontology.og_ontology_relationship o ON o.id = r.ontology_relationship_id AND o.graph_id = ? "
                        + "WHERE r.graph_id = ? AND o.id IS NULL)",
                Boolean.class, entityGraph.getLinkGraphId(), entityGraph.getId());
        if (Boolean.TRUE.equals(invalidNodeLink) || Boolean.TRUE.equals(invalidRelationshipLink)) throw denied();
    }

    private void requireNodesInGraph(final List<BigInteger> nodeIds, final BigInteger graphId) {
        if (nodeIds.stream().anyMatch(Objects::isNull)) throw denied();
        String table = getGraphType() == GraphTypeEnum.ONTOLOGY ? "og_ontology" : "og_entity";
        requireIdsInTable(nodeIds.stream().distinct().toList(), graphId, table, "graph_id");
    }

    private void requireIdsInGraph(final List<BigInteger> ids, final BigInteger graphId, final boolean relationship) {
        String table = relationship
                ? (getGraphType() == GraphTypeEnum.ONTOLOGY ? "og_ontology_relationship" : "og_entity_relationship")
                : (getGraphType() == GraphTypeEnum.ONTOLOGY ? "og_ontology" : "og_entity");
        requireIdsInTable(ids, graphId, table, "graph_id");
    }

    private void requireIdsInTable(final List<BigInteger> requestedIds, final BigInteger graphId,
                                   final String table, final String graphColumn) {
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.stream().anyMatch(Objects::isNull)) throw denied();
        List<BigInteger> distinct = requestedIds.stream().distinct().toList();
        if (distinct.size() != requestedIds.size()) throw denied();
        String marks = String.join(",", java.util.Collections.nCopies(distinct.size(), "?"));
        List<Object> args = new ArrayList<>(distinct);
        args.add(graphId);
        Long found = jdbc.queryForObject("SELECT COUNT(*) FROM ontology." + table + " WHERE id IN (" + marks + ") AND " + graphColumn + " = ?", Long.class, args.toArray());
        if (found == null || found != distinct.size()) throw denied();
    }

    private void requireDeclaredGraph(final BigInteger requestedGraph, final BigInteger actualGraph) {
        if (requestedGraph == null || !requestedGraph.equals(actualGraph)) throw denied();
    }

    private BigInteger nodeGraphId(final BigInteger nodeId) {
        String table = getGraphType() == GraphTypeEnum.ONTOLOGY ? "og_ontology" : "og_entity";
        return one("SELECT graph_id FROM ontology." + table + " WHERE id = ?", nodeId);
    }

    private BigInteger relationshipGraphId(final BigInteger relationshipId) {
        String table = getGraphType() == GraphTypeEnum.ONTOLOGY ? "og_ontology_relationship" : "og_entity_relationship";
        return one("SELECT graph_id FROM ontology." + table + " WHERE id = ?", relationshipId);
    }

    private Graph graph(final BigInteger id) {
        if (id == null) throw denied();
        Graph graph = jdbc.query("SELECT id, type, organization_id, link_graph_id FROM ontology.og_graph WHERE id = ?",
                        (rs, rowNum) -> {
                            Graph row = new Graph();
                            row.setId(rs.getBigDecimal("id").toBigInteger());
                            row.setType(rs.getString("type"));
                            row.setOrganizationId(rs.getBigDecimal("organization_id").toBigInteger());
                            var link = rs.getBigDecimal("link_graph_id");
                            row.setLinkGraphId(link == null ? null : link.toBigInteger());
                            return row;
                        }, id)
                .stream().findFirst().orElseThrow(AuthorizedGraphStrategy::denied);
        try {
            GraphTypeEnum.valueOf(graph.getType());
        } catch (RuntimeException e) {
            throw denied();
        }
        return graph;
    }

    private Graph strategyGraph(final BigInteger id) {
        Graph graph = graph(id);
        if (!getGraphType().name().equals(graph.getType())) throw denied();
        return graph;
    }

    private BigInteger one(final String sql, final BigInteger id) {
        return jdbc.query(sql, (rs, rowNum) -> rs.getBigDecimal(1).toBigInteger(), id)
                .stream().findFirst().orElseThrow(AuthorizedGraphStrategy::denied);
    }

    private long count(final String sql, final Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private void requireType(final GraphTypeEnum requested) {
        if (requested != getGraphType()) throw denied();
    }

    private void audit(final String eventType, final BigInteger projectId, final String capability) {
        organizationDao.insertProjectAudit(eventType, access.currentUser().id(), projectId, capability);
    }

    private <T> T tx(final Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Project access denied");
    }
}
