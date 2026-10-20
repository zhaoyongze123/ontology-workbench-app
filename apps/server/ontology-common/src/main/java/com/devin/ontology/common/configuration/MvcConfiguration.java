package com.devin.ontology.common.configuration;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.math.BigInteger;

/**
 * 2025/4/25 10:53.
 *
 * <p></p>
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class MvcConfiguration implements WebMvcConfigurer {

    // 页面与 API 通过同源入口访问；不再向任意来源开放带凭据跨域请求。

    /**
     * 配置Jackson序列化器.
     * @return Jackson2ObjectMapperBuilderCustomizer
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            SimpleModule simpleModule = new SimpleModule();
            // simpleModule.addSerializer(long.class, ToStringSerializer.instance);
            simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
            simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            simpleModule.addSerializer(BigInteger.class, ToStringSerializer.instance);
            builder.modules(new Jdk8Module(), new JavaTimeModule(), simpleModule);
        };
    }
}
