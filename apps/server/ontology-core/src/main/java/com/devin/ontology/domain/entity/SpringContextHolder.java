package com.devin.ontology.domain.entity;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 2025/6/11 21:16.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    @Getter
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull final ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.applicationContext = applicationContext;
    }

    /**
     * 获取bean.
     *
     * @param clazz Bean的class
     * @param <T>   T
     * @return Bean
     */
    public static <T> T getBean(final Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    /**
     * 获取bean.
     *
     * @param name  bean名称
     * @param clazz class
     * @param <T>   T
     * @return Bean
     */
    public static <T> T getBean(final String name, final Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }
}
