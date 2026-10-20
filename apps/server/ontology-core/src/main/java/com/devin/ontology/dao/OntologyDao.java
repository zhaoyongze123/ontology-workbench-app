package com.devin.ontology.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devin.ontology.domain.entity.Ontology;
import com.devin.ontology.domain.vo.NodeQueryVO;
import com.devin.ontology.mapper.OntologyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/16 18:32:59.
 *
 * <p>
 *  本体对象(Ontology)Dao层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service("ontologyDao-v2")
@RequiredArgsConstructor
public class OntologyDao extends ServiceImpl<OntologyMapper, Ontology> {

    /**
     * 分页查询.
     * @param queryVO 查询参数
     * @return 分页结果
     */
    public Page<Ontology> pageByQuery(final NodeQueryVO queryVO) {
        Page<Ontology> page = new Page<>(
                queryVO.getPageNum(),
                queryVO.getPageSize()
        );
        return lambdaQuery()
                .eq(Ontology::getGraphId, queryVO.getGraphId())
                .in(!CollectionUtils.isEmpty(queryVO.getNodeIdList()), Ontology::getId, queryVO.getNodeIdList())
                .orderByDesc(Ontology::getUpdateTime)
                .page(page);
    }

    /**
     * 根据图谱id查询.
     * @param graphId 图谱id
     * @return List
     */
    public List<Ontology> getByGraphId(final BigInteger graphId) {
        return lambdaQuery()
                .eq(Ontology::getGraphId, graphId)
                .orderByDesc(Ontology::getUpdateTime)
                .list();
    }

    /**
     * 根据图谱id和名称查询.
     * @param graphId 图谱id
     * @param name 名称
     * @return 实体
     */
    public Ontology getByGraphIdAndName(final BigInteger graphId, final String name) {
        return lambdaQuery()
                .eq(Ontology::getGraphId, graphId)
                .eq(Ontology::getName, name)
                .one();
    }
}
