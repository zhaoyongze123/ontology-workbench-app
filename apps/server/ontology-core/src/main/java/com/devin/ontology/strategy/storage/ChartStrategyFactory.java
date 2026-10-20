package com.devin.ontology.strategy.storage;

import com.devin.ontology.common.enums.ChartTypeEnum;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 2025/6/5 10:25.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 * @deprecated 暂时舍弃工厂模式生产策略的方案，直接使用@Profile注解优化，由Spring直接进行判断需要使用的策略类
 */
@Deprecated
@Component
public class ChartStrategyFactory {

    private final Map<String, ChartStorageStrategy> chartStorageStrategyMap;

    public ChartStrategyFactory(final Map<String, ChartStorageStrategy> chartStorageStrategyMap) {
        this.chartStorageStrategyMap = chartStorageStrategyMap;
    }

    /**
     * 获取存储策略.
     *
     * @param chartTypeEnum 存储类型枚举
     * @return 存储策略
     */
    public ChartStorageStrategy getStrategy(final ChartTypeEnum chartTypeEnum) {
        return chartStorageStrategyMap.get(chartTypeEnum.getStrategy());
    }
}
