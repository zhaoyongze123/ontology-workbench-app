package com.devin.ontology.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Locale;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 把多用户业务 API 的错误协商契约写入生成的 OpenAPI 文档。 */
@Configuration
@Profile("multiuser")
public class ApiErrorOpenApiConfiguration {
    private static final String PROBLEM_SCHEMA = "OntologyProblemDetails";
    private static final String FIELD_ERROR_SCHEMA = "OntologyProblemFieldError";
    private static final String LEGACY_API_RESULT_SCHEMA = "OntologyLegacyApiError";
    private static final String LEGACY_ERROR_SCHEMA = "OntologyLegacyErrorEnvelope";
    private static final List<String> ERROR_STATUSES = List.of(
            "400", "401", "403", "404", "405", "406", "409", "413", "415", "422", "429", "500", "503");

    @Bean
    public OpenApiCustomizer multiuserApiErrorCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            addSchemas(components);
            if (openApi.getPaths() == null) return;

            openApi.getPaths().forEach((path, pathItem) -> {
                // OAuth/OIDC 协议错误由各自的协议处理器描述，不能套用普通业务错误响应。
                if (isProtocolPath(path)) return;
                pathItem.readOperationsMap().values().forEach(operation -> {
                    if (operation.getResponses() == null) operation.setResponses(new ApiResponses());
                    ApiResponses responses = operation.getResponses();
                    ERROR_STATUSES.forEach(status -> {
                        if (!responses.containsKey(status)) responses.addApiResponse(status, businessErrorResponse(status));
                    });
                });
            });
        };
    }

    private void addSchemas(final Components components) {
        if (components.getSchemas() != null && components.getSchemas().containsKey(PROBLEM_SCHEMA)) return;
        components.addSchemas(FIELD_ERROR_SCHEMA, new ObjectSchema()
                .description("单个字段校验错误；不包含用户提交的拒绝值。")
                .addProperty("field", new StringSchema().description("字段路径"))
                .addProperty("code", new StringSchema().description("稳定原因码"))
                .addProperty("message", new StringSchema().description("可安全展示的字段提示"))
                .required(List.of("field", "code", "message")));
        components.addSchemas(PROBLEM_SCHEMA, new ObjectSchema()
                .description("RFC 9457 Problem Details；通过 Accept: application/problem+json 协商。")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("errorCode", new StringSchema().description("Ontology 稳定错误码"))
                .addProperty("requestId", new StringSchema().description("与 X-Request-ID 响应头一致"))
                .addProperty("errors", new ArraySchema().items(reference(FIELD_ERROR_SCHEMA)))
                .required(List.of("type", "title", "status", "detail", "instance", "errorCode", "requestId")));
        components.addSchemas(LEGACY_API_RESULT_SCHEMA, new ObjectSchema()
                .description("旧版 ApiResult 错误体，成功结构保持不变。")
                .addProperty("success", new io.swagger.v3.oas.models.media.BooleanSchema())
                .addProperty("code", new IntegerSchema())
                .addProperty("message", new StringSchema())
                .addProperty("data", new ObjectSchema())
                .required(List.of("success", "code", "message")));
        components.addSchemas(LEGACY_ERROR_SCHEMA, new ObjectSchema()
                .description("旧版 OAuth/UI 错误 envelope；不用于 OAuth/OIDC 协议端点。")
                .addProperty("error", new StringSchema())
                .required(List.of("error")));
    }

    private ApiResponse businessErrorResponse(final String status) {
        Content content = new Content()
                .addMediaType("application/problem+json",
                        new MediaType().schema(reference(PROBLEM_SCHEMA)))
                .addMediaType("application/json", new MediaType().schema(new ComposedSchema()
                        .description("未协商 Problem Details 时的旧响应；具体 envelope 依端点兼容约定。")
                        .oneOf(List.of(reference(LEGACY_API_RESULT_SCHEMA), reference(LEGACY_ERROR_SCHEMA)))));
        return new ApiResponse()
                .description("HTTP " + status + " 业务错误；成功响应结构不受影响。")
                .addHeaderObject("X-Request-ID", new Header().description("请求关联号")
                        .schema(new StringSchema()))
                .content(content);
    }

    private Schema<?> reference(final String schemaName) {
        return new Schema<>().$ref("#/components/schemas/" + schemaName);
    }

    private boolean isProtocolPath(final String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("/oauth2/") || normalized.contains("/oidc/")
                || normalized.contains("/.well-known/");
    }
}
