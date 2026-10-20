package com.devin.ontology.common.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 2025/9/29 17:12.
 *
 * <p>
 *     加载Age插件切面类
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
public class LoadAgeAspect {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 切点.
     */
    @Pointcut("execution (* com.devin.*.strategy.chart.impl.AgeStrategy.*(..))")
    public void pointCut() {
    }

    /**
     * 方法前置插件加载.
     */
    @Before("pointCut()")
    public void before() {
        // 执行加载age
        String loadAge = "SET search_path = ag_catalog, \"$user\", %s;".formatted(
                System.getenv("POSTGRESQL_SCHEMA")
        );
        log.info("==> Loading Age Plugs: {}", loadAge);
        try {
            jdbcTemplate.execute(loadAge);
        } catch (Exception e) {
            log.error("<== Age Plugs Set Fail: ", e);
        }
        log.info("<== Age Plugs Set Completed");
    }
}
