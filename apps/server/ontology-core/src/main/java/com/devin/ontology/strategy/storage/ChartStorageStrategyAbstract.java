package com.devin.ontology.strategy.storage;

import com.devin.ontology.domain.dto.NodeDTO;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.common.utils.StringTemplateFormatUtils;
import java.util.List;
import java.util.Objects;

/**
 * 2025/9/29 12:08.
 *
 * <p>
 *     图表存储策略抽象类
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class ChartStorageStrategyAbstract implements ChartStorageStrategy {

    /**
     * 生成数据库名称.
     * @param dbPrefix 数据库前缀
     * @param databaseId 数据库ID
     * @return 数据库名称
     */
    protected String generateDatabase(final String dbPrefix, final Long databaseId) {
        return StringTemplateFormatUtils.builder()
                .template("$dbPrefix$databaseId")
                .param("dbPrefix", dbPrefix)
                .param("databaseId", databaseId)
                .build();
    }

    /**
     * 校验同库.
     * @param nodeDTOList 节点列表
     * @return 数据库ID
     */
    protected Long checkDatabaseEquals(final List<NodeDTO> nodeDTOList) {
        if (nodeDTOList == null || nodeDTOList.isEmpty()) {
            return null;
        }
        // 校验同库
        Long databaseId = nodeDTOList.get(0).getDatabaseId();
        if (nodeDTOList.stream().anyMatch(n -> !Objects.equals(n.getDatabaseId(), databaseId))) {
            throw new ValidateException("同一批次的节点必须属于同一个数据库");
        }
        return databaseId;
    }
}
