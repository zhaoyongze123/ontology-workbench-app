package com.devin.ontology.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.ApiErrorResponseWriter;
import com.devin.ontology.common.exception.OntologyApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.sql.SQLException;

/** 透过 Spring MVC 请求分派验证状态、内容协商、字段错误和安全消息。 */
class MultiuserApiExceptionAdviceTest {
    private MockMvc mvc;
    private com.devin.ontology.identity.AccountLifecycleService accountService;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasenames("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper(), messages);
        accountService = mock(com.devin.ontology.identity.AccountLifecycleService.class);
        when(accountService.setEnabled("u1", false))
                .thenThrow(new OntologyApiException(ApiErrorCode.ACCOUNT_LAST_ADMIN));
        mvc = MockMvcBuilders.standaloneSetup(new ErrorProbeController(),
                        new com.devin.ontology.identity.AccountLifecycleController(accountService))
                .setControllerAdvice(new MultiuserApiExceptionAdvice(writer)).build();
    }

    @Test
    void explicitlyNegotiatedProblemUsesStableCodeAndSameRequestIdAsHeader() throws Exception {
        var result = mvc.perform(get("/test-errors/account-conflict")
                        .accept(MediaType.valueOf("application/problem+json"), MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LAST_ADMIN"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.instance").exists())
                .andReturn();
        String headerRequestId = result.getResponse().getHeader("X-Request-ID");
        String bodyRequestId = new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .get("requestId").asText();
        assertEquals(headerRequestId, bodyRequestId);
    }

    @Test
    void legacyAcceptKeepsApiResultShapeWhileUsingCorrectHttpStatus() throws Exception {
        mvc.perform(get("/test-errors/account-conflict").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void accountControllerContractKeepsLegacyErrorFieldForJsonClients() throws Exception {
        mvc.perform(patch("/admin/users/u1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void englishLanguageUsesTheEnglishErrorCatalogForGenericLanguageTags() throws Exception {
        mvc.perform(get("/test-errors/account-conflict")
                        .header("Accept-Language", "en")
                        .accept(MediaType.valueOf("application/problem+json")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("At least one enabled system administrator must remain."));
    }

    @Test
    void validationReturnsEverySafeFieldErrorWithoutRejectedValues() throws Exception {
        mvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.valueOf("application/problem+json"))
                        .content("{\"name\":\"sensitive-rejected-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sensitive-rejected-value"))));
    }

    @Test
    void unknownExceptionReturnsSafe500AndNeverReflectsThrowableText() throws Exception {
        mvc.perform(get("/test-errors/internal").accept(MediaType.valueOf("application/problem+json")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database-secret-marker"))));
    }

    @Test
    void databaseSyntaxStateOutsideQueryEndpointRemainsAnInternalFailure() throws Exception {
        mvc.perform(get("/test-errors/database-syntax").accept(MediaType.valueOf("application/problem+json")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal database marker"))));
    }

    @Test
    void connectionFailureIsReportedAsTemporaryDependencyUnavailable() throws Exception {
        mvc.perform(get("/test-errors/database-connection").accept(MediaType.valueOf("application/problem+json")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void missingRequiredRequestParameterIsAClientError() throws Exception {
        mvc.perform(get("/test-errors/missing-parameter").accept(MediaType.valueOf("application/problem+json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_INVALID"));
    }

    @Test
    void databaseUniqueViolationIsAConflictWithoutLeakingConstraintDetails() throws Exception {
        mvc.perform(get("/test-errors/unique-conflict").accept(MediaType.valueOf("application/problem+json")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal unique marker"))));
    }

    @RestController
    static class ErrorProbeController {
        @GetMapping("/test-errors/account-conflict")
        void conflict() {
            throw new OntologyApiException(ApiErrorCode.ACCOUNT_LAST_ADMIN);
        }

        @PostMapping("/test-errors/validation")
        void validation(@Valid @RequestBody final ValidationRequest request) { }

        @GetMapping("/test-errors/internal")
        void internal() {
            throw new IllegalStateException("database-secret-marker");
        }

        @GetMapping("/test-errors/database-syntax")
        void databaseSyntax() {
            throw new DataAccessResourceFailureException("internal database marker",
                    new SQLException("internal database marker", "42601"));
        }

        @GetMapping("/test-errors/database-connection")
        void databaseConnection() {
            throw new DataAccessResourceFailureException("internal database marker",
                    new SQLException("internal database marker", "08006"));
        }

        @GetMapping("/test-errors/missing-parameter")
        void missingParameter(@RequestParam final String required) { }

        @GetMapping("/test-errors/unique-conflict")
        void uniqueConflict() {
            throw new DataAccessResourceFailureException("internal unique marker",
                    new SQLException("internal unique marker", "23505"));
        }
    }

    record ValidationRequest(@Pattern(regexp = "approved", message = "拒绝值：${validatedValue}") String name) { }
}
