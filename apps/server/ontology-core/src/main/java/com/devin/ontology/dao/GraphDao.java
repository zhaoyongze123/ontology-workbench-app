package com.devin.ontology.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.domain.entity.Graph;
import com.devin.ontology.domain.vo.GraphQueryVO;
import com.devin.ontology.mapper.GraphMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 2025/11/16 00:42:32.
 *
 * <p>
 * 图谱(Graph)Dao层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphDao extends ServiceImpl<GraphMapper, Graph> {

    private final OrganizationDao organizationDao;

    /**
     * 根据组织id查询图谱.
     *
     * @param organizationIdList 组织id列表
     * @return 图谱列表
     */
    public List<Graph> getByOrganizationIds(final List<BigInteger> organizationIdList) {
        if (organizationIdList.isEmpty()) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .in(Graph::getOrganizationId, organizationIdList)
                .list();
    }

    /**
     * 更新图谱时间.
     *
     * @param graphId 图谱id
     */
    public void updateTime(final BigInteger graphId) {
        lambdaUpdate()
                .eq(Graph::getId, graphId)
                .set(Graph::getUpdateTime, new Date())
                .update();
        // 更新组织项目时间
        organizationDao.updateTime(
                getById(graphId).getOrganizationId()
        );
    }

    /**
     * 分页查询图谱.
     *
     * @param queryVO 查询参数
     * @return 分页结果
     */
    public Page<Graph> pageByQuery(final GraphQueryVO queryVO) {
        Page<Graph> page = new Page<>(
                queryVO.getPageNum(),
                queryVO.getPageSize()
        );

        return lambdaQuery()
                .eq(Graph::getOrganizationId, queryVO.getOrganizationId())
                .eq(Graph::getType, queryVO.getGraphType().name())
                .orderByDesc(Graph::getUpdateTime)
                .orderByDesc(Graph::getId)
                .page(page);
    }

    /**
     * 根据类型获取图谱信息.
     *
     * @param organizationId 组织ID
     * @param graphTypeEnum  图谱类型
     * @return 图谱信息
     */
    public List<Graph> getByType(final BigInteger organizationId, final GraphTypeEnum graphTypeEnum) {
        return lambdaQuery()
                .eq(Graph::getOrganizationId, organizationId)
                .eq(Graph::getType, graphTypeEnum.name())
                .list();
    }
}
