package com.devin.ontology.common.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 2025/6/13 22:12.
 *
 * <p></p>
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class ThreadPoolConfiguration {

    /**
     * 图数据库查询线程池.
     * @return ThreadPoolTaskExecutor
     */
    @Bean("chartStorageTaskExecutor")
    public ThreadPoolTaskExecutor chartStorageTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(10);
        taskExecutor.setMaxPoolSize(25);
        taskExecutor.setQueueCapacity(1);
        taskExecutor.setThreadPriority(Thread.MAX_PRIORITY);
        taskExecutor.setDaemon(false);
        taskExecutor.setAllowCoreThreadTimeOut(false);
        taskExecutor.setThreadNamePrefix("chart-storage-task-");

        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        taskExecutor.initialize();
        return taskExecutor;
    }
}
