package com.devin.ontology.common.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 2025/4/25 10:47.
 *
 * <p></p>
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class Knife4jConfiguration {

    /**
     * swagger配置.
     * @return OpenAPI
     */
    @Bean
    public OpenAPI customOpenAPI() {

        // 设置联系方式
        Contact contact = new Contact();
//        contact.setName("devin");
//        contact.setEmail("wzh.devin@gmail.com");
//        contact.setUrl("https://github.com/wzh-devin");

        // 配置信息
        return new OpenAPI()
                .info(new Info()
                        .title("Ontology")
                        .version("1.0")
                        .description("意言科技知识图谱接口文档。多用户模式的普通业务 API 支持通过 Accept: application/problem+json 协商 RFC 9457 错误响应；旧版 JSON 错误体保留兼容。OAuth/OIDC 协议端点保持各自的协议响应。"));
    }
}
