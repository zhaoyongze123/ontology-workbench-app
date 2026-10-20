package com.devin.ontology.project;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.dao.OrganizationDao;
import com.devin.ontology.domain.entity.Graph;
import com.devin.ontology.domain.vo.GraphSaveVO;
import com.devin.ontology.domain.vo.NodeSaveVO;
import com.devin.ontology.domain.vo.RelationshipSaveVO;
import com.devin.ontology.strategy.graph.GraphStrategy;
import com.devin.ontology.strategy.graph.impl.OntologyGraphStrategy;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class AuthorizedGraphStrategyTest {
    @SuppressWarnings("unchecked")
    private TransactionTemplate directTransaction() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(final TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void rejectsRelationshipWithEndpointOutsideDeclaredGraph() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        GraphStrategy entityDelegate = mock(GraphStrategy.class);
        when(entityDelegate.getGraphType()).thenReturn(GraphTypeEnum.ENTITY);
        Graph graph = graph(10, GraphTypeEnum.ENTITY, 3, 20L);
        when(jdbc.query(contains("FROM ontology.og_graph"), any(RowMapper.class), eq(BigInteger.TEN)))
                .thenReturn(List.of(graph));
        when(jdbc.queryForObject(contains("FROM ontology.og_entity WHERE id IN"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        AuthorizedGraphStrategy service = new AuthorizedGraphStrategy(entityDelegate, jdbc, access,
                mock(OrganizationDao.class), directTransaction(), mock(OntologyGraphStrategy.class));
        RelationshipSaveVO vo = new RelationshipSaveVO();
        vo.setGraphId(BigInteger.TEN);
        vo.setGraphType(GraphTypeEnum.ENTITY);
        vo.setSourceNodeId(BigInteger.ONE);
        vo.setTargetNodeId(BigInteger.TWO);

        assertThrows(AccessDeniedException.class, () -> service.saveRelationship(vo));
        verify(entityDelegate, never()).saveRelationship(vo);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void rejectsEntityGraphLinkedToOntologyFromAnotherProject() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        GraphStrategy entityDelegate = mock(GraphStrategy.class);
        when(entityDelegate.getGraphType()).thenReturn(GraphTypeEnum.ENTITY);
        Graph ontology = graph(20, GraphTypeEnum.ONTOLOGY, 4, null);
        when(jdbc.query(contains("FROM ontology.og_graph"), any(RowMapper.class), eq(BigInteger.valueOf(20))))
                .thenReturn(List.of(ontology));
        AuthorizedGraphStrategy service = new AuthorizedGraphStrategy(entityDelegate, jdbc, access,
                mock(OrganizationDao.class), directTransaction(), mock(OntologyGraphStrategy.class));
        GraphSaveVO vo = new GraphSaveVO();
        vo.setGraphType(GraphTypeEnum.ENTITY);
        vo.setOrganizationId(BigInteger.valueOf(3));
        vo.setLinkGraphId(BigInteger.valueOf(20));

        assertThrows(AccessDeniedException.class, () -> service.saveGraph(vo));
        verify(entityDelegate, never()).saveGraph(vo);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void rejectsClientTypeThatDoesNotMatchTheStoredGraphType() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        GraphStrategy entityDelegate = mock(GraphStrategy.class);
        when(entityDelegate.getGraphType()).thenReturn(GraphTypeEnum.ENTITY);
        when(jdbc.query(contains("FROM ontology.og_graph"), any(RowMapper.class), eq(BigInteger.TEN)))
                .thenReturn(List.of(graph(10, GraphTypeEnum.ONTOLOGY, 3, null)));
        AuthorizedGraphStrategy service = new AuthorizedGraphStrategy(entityDelegate, jdbc, access,
                mock(OrganizationDao.class), directTransaction(), mock(OntologyGraphStrategy.class));
        NodeSaveVO vo = new NodeSaveVO();
        vo.setGraphId(BigInteger.TEN);
        vo.setGraphType(GraphTypeEnum.ENTITY);

        assertThrows(AccessDeniedException.class, () -> service.saveNode(vo));
        verify(entityDelegate, never()).saveNode(vo);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void rejectsEntityNodeLinkedToOntologyOutsideItsRealParentGraph() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        GraphStrategy entityDelegate = mock(GraphStrategy.class);
        when(entityDelegate.getGraphType()).thenReturn(GraphTypeEnum.ENTITY);
        when(jdbc.query(contains("FROM ontology.og_graph"), any(RowMapper.class), eq(BigInteger.TEN)))
                .thenReturn(List.of(graph(10, GraphTypeEnum.ENTITY, 3, 20L)));
        when(jdbc.query(contains("FROM ontology.og_graph"), any(RowMapper.class), eq(BigInteger.valueOf(20))))
                .thenReturn(List.of(graph(20, GraphTypeEnum.ONTOLOGY, 3, null)));
        when(jdbc.queryForObject(contains("FROM ontology.og_ontology WHERE id = ?"), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        AuthorizedGraphStrategy service = new AuthorizedGraphStrategy(entityDelegate, jdbc, access,
                mock(OrganizationDao.class), directTransaction(), mock(OntologyGraphStrategy.class));
        NodeSaveVO vo = new NodeSaveVO();
        vo.setGraphId(BigInteger.TEN);
        vo.setGraphType(GraphTypeEnum.ENTITY);
        vo.setLinkOntologyId(BigInteger.valueOf(99));

        assertThrows(AccessDeniedException.class, () -> service.saveNode(vo));
        verify(entityDelegate, never()).saveNode(vo);
    }

    private static Graph graph(final long id, final GraphTypeEnum type, final long projectId, final Long linkId) {
        Graph graph = new Graph();
        graph.setId(BigInteger.valueOf(id));
        graph.setType(type.name());
        graph.setOrganizationId(BigInteger.valueOf(projectId));
        graph.setLinkGraphId(linkId == null ? null : BigInteger.valueOf(linkId));
        return graph;
    }
}
