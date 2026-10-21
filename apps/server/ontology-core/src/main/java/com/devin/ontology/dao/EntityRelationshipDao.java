package com.devin.ontology.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devin.ontology.domain.entity.EntityRelationship;
import com.devin.ontology.domain.vo.RelationshipQueryVO;
import com.devin.ontology.mapper.EntityRelationshipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/19 22:06:58.
 *
 * <p>
 * 实体对象关系(EntityRelationship)Dao层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityRelationshipDao extends ServiceImpl<EntityRelationshipMapper, EntityRelationship> {

    /**
     * 分页查询.
     *
     * @param queryVO 查询参数
     * @return 分页结果
     */
    public Page<EntityRelationship> pageByQuery(final RelationshipQueryVO queryVO) {
        Page<EntityRelationship> page = new Page<>(
                queryVO.getPageNum(),
                queryVO.getPageSize()
        );

        return lambdaQuery()
                .eq(EntityRelationship::getGraphId, queryVO.getGraphId())
                .in(!CollectionUtils.isEmpty(queryVO.getRelationshipIdList()), EntityRelationship::getId, queryVO.getRelationshipIdList())
                .orderByDesc(EntityRelationship::getUpdateTime)
                .orderByDesc(EntityRelationship::getId)
                .page(page);
    }

    /**
     * 根据节点ID查询关系.
     *
     * @param sourceNodeId 源节点ID
     * @param targetNodeId 目标节点ID
     * @return 关系列表
     */
    public List<EntityRelationship> getByNodeId(
            final BigInteger sourceNodeId,
            final BigInteger targetNodeId
    ) {
        return lambdaQuery()
                .select(EntityRelationship::getOntologyRelationshipId)
                .eq(EntityRelationship::getSourceNodeId, sourceNodeId)
                .eq(EntityRelationship::getTargetNodeId, targetNodeId)
                .list();
    }

    /**
     * 根据图谱id查询.
     *
     * @param graphId 图谱id
     * @return 实体列表
     */
    public List<EntityRelationship> getByGraphId(final BigInteger graphId) {
        return lambdaQuery()
                .eq(EntityRelationship::getGraphId, graphId)
                .orderByDesc(EntityRelationship::getUpdateTime)
                .orderByDesc(EntityRelationship::getId)
                .list();
    }
}
