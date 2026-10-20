package com.devin.ontology.common.configuration;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026/10/16 16:29:29.
 *
 * <p>
 * 在连接池初始化前配置 AGE，确保每条新物理连接都加载扩展。
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "graph.database.type", havingValue = "age")
public class AgeConnectionInitializer implements BeanPostProcessor {
    private final boolean preloaded;

    public AgeConnectionInitializer() {
        this.preloaded = false;
    }

    @Autowired
    public AgeConnectionInitializer(final Environment environment) {
        // BeanPostProcessor 创建较早，直接读 Environment，避免依赖尚未注册的占位符处理器。
        this.preloaded = environment.getProperty("graph.database.age.preloaded", Boolean.class, false);
    }

    /**
     * 保留已有连接初始化语句，并在其执行前加载 AGE。
     *
     * @param bean 待初始化的组件
     * @param beanName 组件名称
     * @return 原组件
     */
    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName) {
        if (bean instanceof DruidDataSource dataSource) {
            List<String> sqlList = new ArrayList<>();
            // 已由数据库 shared_preload_libraries 加载时，不要求运行账号拥有 LOAD 超级权限。
            if (!preloaded) {
                sqlList.add("LOAD 'age';");
            }
            sqlList.addAll(dataSource.getConnectionInitSqls());
            dataSource.setConnectionInitSqls(sqlList);
        }
        return bean;
    }
}
