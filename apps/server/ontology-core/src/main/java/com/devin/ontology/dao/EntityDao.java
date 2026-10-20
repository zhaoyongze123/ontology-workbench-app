package com.devin.ontology.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devin.ontology.domain.entity.Entity;
import com.devin.ontology.domain.vo.NodeQueryVO;
import com.devin.ontology.mapper.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/19 23:17:59.
 *
 * <p>
 *  实体对象(Entity)Dao层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDao extends ServiceImpl<EntityMapper, Entity> {

    /**
     * 分页查询.
     * @param queryVO 查询参数
     * @return 分页结果
     */
    public Page<Entity> pageByQuery(final NodeQueryVO queryVO) {
        Page<Entity> page = new Page<>(
                queryVO.getPageNum(),
                queryVO.getPageSize()
        );

        return lambdaQuery()
                .eq(Entity::getGraphId, queryVO.getGraphId())
                .in(!CollectionUtils.isEmpty(queryVO.getNodeIdList()), Entity::getId, queryVO.getNodeIdList())
                .in(!CollectionUtils.isEmpty(queryVO.getLinkOntologyNodeIdList()), Entity::getOntologyId, queryVO.getLinkOntologyNodeIdList())
                .orderByDesc(Entity::getUpdateTime)
                .page(page);
    }

    /**
     * 根据图谱id查询.
     * @param graphId 图谱id
     * @return 实体列表
     */
    public List<Entity> getByGraphId(final BigInteger graphId) {
        return lambdaQuery()
                .eq(Entity::getGraphId, graphId)
                .orderByDesc(Entity::getUpdateTime)
                .list();
    }
}
