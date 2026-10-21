package com.devin.ontology.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

/** 检查普通业务错误契约可见，且 OAuth/OIDC 的 OpenAPI 响应保持原样。 */
class ApiErrorOpenApiConfigurationTest {
    @Test
    void publishesReusableErrorSchemasWithoutRewritingProtocolResponses() {
        Operation business = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("成功")));
        Operation userInfo = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("OIDC 用户信息")));
        Paths paths = new Paths()
                .addPathItem("/api/v1/admin/users", new PathItem().get(business))
                .addPathItem("/api/v1/oidc/app/userinfo", new PathItem().get(userInfo));
        OpenAPI openApi = new OpenAPI().components(new Components()).paths(paths);

        new ApiErrorOpenApiConfiguration().multiuserApiErrorCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsKeys(
                "OntologyProblemDetails", "OntologyProblemFieldError", "OntologyLegacyApiError");
        assertThat(business.getResponses()).containsKeys("200", "400", "401", "409", "422", "500");
        assertThat(business.getResponses().get("400").getContent().keySet())
                .contains("application/problem+json", "application/json");
        assertThat(userInfo.getResponses()).containsOnlyKeys("200");
    }
}
