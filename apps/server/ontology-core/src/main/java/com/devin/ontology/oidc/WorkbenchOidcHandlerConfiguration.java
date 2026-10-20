package com.devin.ontology.oidc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/** OIDC 专属处理器和存储策略。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ontology.workbench", name = "enabled", havingValue = "true")
class WorkbenchOidcHandlerConfiguration {
    @Bean
    SecurityContextRepository workbenchSecurityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    OAuth2AuthorizedClientRepository workbenchAuthorizedClientRepository() {
        return new WorkbenchAuthorizedClientRepository();
    }

    @Bean
    OidcUserService workbenchOidcUserService() {
        return new OidcUserService();
    }

    @Bean
    AuthenticationSuccessHandler workbenchOidcSuccessHandler(final WorkbenchIdentityService identities,
            final WorkbenchOidcProperties properties, final ObjectMapper mapper,
            final SecurityContextRepository repository) {
        return new WorkbenchOidcSuccessHandler(identities, properties, mapper, repository);
    }

    @Bean
    AuthenticationFailureHandler workbenchOidcFailureHandler(final ObjectMapper mapper) {
        return new WorkbenchOidcFailureHandler(mapper);
    }
}
