package com.devin.ontology.strategy.graph;

import com.devin.ontology.dao.OrganizationDao;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.domain.vo.NodeVO;
import com.devin.ontology.domain.vo.OptionalVO;
import com.devin.ontology.project.AuthorizedGraphStrategy;
import com.devin.ontology.project.ProjectAccessService;
import com.devin.ontology.strategy.graph.impl.OntologyGraphStrategy;
import java.math.BigInteger;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;

/**
 * 2025/11/16 02:20.
 *
 * <p>
 *     图谱策略工厂
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
public class GraphStrategyFactory {

    private final Map<String, GraphStrategy> graphStrategyMap;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectAccessService projectAccessService;
    private final OrganizationDao organizationDao;
    private final TransactionTemplate transactionTemplate;
    private final OntologyGraphStrategy ontologyDelegate;

    public GraphStrategyFactory(final Map<String, GraphStrategy> graphStrategyMap,
                                final JdbcTemplate jdbcTemplate,
                                final ProjectAccessService projectAccessService,
                                final OrganizationDao organizationDao,
                                final PlatformTransactionManager transactionManager,
                                final OntologyGraphStrategy ontologyDelegate) {
        this.graphStrategyMap = graphStrategyMap;
        this.jdbcTemplate = jdbcTemplate;
        this.projectAccessService = projectAccessService;
        this.organizationDao = organizationDao;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.ontologyDelegate = ontologyDelegate;
    }

    /**
     * 获取图谱策略.
     *
     * @param graphTypeEnum 图谱类型枚举
     * @return 图谱策略
     */
    public GraphStrategy getStrategy(final GraphTypeEnum graphTypeEnum) {
        GraphStrategy strategy = graphStrategyMap.get(graphTypeEnum.getStrategy());
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown graph strategy: " + graphTypeEnum);
        }
        return new AuthorizedGraphStrategy(strategy, jdbcTemplate, projectAccessService,
                organizationDao, transactionTemplate, ontologyDelegate);
    }

    /** 图谱间 link 数据通过同一个受权策略边界执行。 */
    public List<NodeVO> getLinkOntologyNodeList(final BigInteger entityGraphId) {
        return ((AuthorizedGraphStrategy) getStrategy(GraphTypeEnum.ENTITY))
                .getLinkOntologyByEntityGraphId(entityGraphId);
    }

    public List<OptionalVO> getLinkOntologyRelationshipList(final BigInteger sourceNodeId,
                                                            final BigInteger targetNodeId) {
        return ((AuthorizedGraphStrategy) getStrategy(GraphTypeEnum.ENTITY))
                .getLinkOntologyRelationshipList(sourceNodeId, targetNodeId);
    }
}
