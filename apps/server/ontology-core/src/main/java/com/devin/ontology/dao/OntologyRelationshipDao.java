package com.devin.ontology.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devin.ontology.domain.entity.OntologyRelationship;
import com.devin.ontology.domain.vo.RelationshipQueryVO;
import com.devin.ontology.mapper.OntologyRelationshipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/19 19:22:04.
 *
 * <p>
 * 本体对象关系(OntologyRelationship)Dao层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OntologyRelationshipDao extends ServiceImpl<OntologyRelationshipMapper, OntologyRelationship> {

    /**
     * 分页查询本体关系对象.
     *
     * @param queryVO 查询参数
     * @return 本体关系对象
     */
    public Page<OntologyRelationship> pageByQuery(final RelationshipQueryVO queryVO) {
        Page<OntologyRelationship> page = new Page<>(
                queryVO.getPageNum(),
                queryVO.getPageSize()
        );

        return lambdaQuery()
                .eq(OntologyRelationship::getGraphId, queryVO.getGraphId())
                .in(!CollectionUtils.isEmpty(queryVO.getRelationshipIdList()), OntologyRelationship::getId, queryVO.getRelationshipIdList())
                .orderByDesc(OntologyRelationship::getUpdateTime)
                .page(page);
    }

    /**
     * 根据图谱id查询.
     *
     * @param graphId 图谱id
     * @return 本体关系对象
     */
    public List<OntologyRelationship> getByGraphId(final BigInteger graphId) {
        return lambdaQuery()
                .eq(OntologyRelationship::getGraphId, graphId)
                .list();
    }

    /**
     * 根据图谱id和关系名称查询.
     *
     * @param graphId 图谱id
     * @param name    关系名称
     * @return 本体关系对象
     */
    public OntologyRelationship getByGraphIdAndName(final BigInteger graphId, final String name) {
        return lambdaQuery()
                .eq(OntologyRelationship::getGraphId, graphId)
                .eq(OntologyRelationship::getName, name)
                .one();
    }
}
