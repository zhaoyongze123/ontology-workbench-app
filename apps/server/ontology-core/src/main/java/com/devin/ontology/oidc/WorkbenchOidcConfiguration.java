package com.devin.ontology.oidc;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Workbench OIDC 仅在运维显式启用后注册客户端。 */
@Configuration
@ConditionalOnProperty(prefix = "ontology.workbench", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WorkbenchOidcProperties.class)
public class WorkbenchOidcConfiguration {
    public WorkbenchOidcConfiguration(final WorkbenchOidcProperties properties) {
        properties.validate();
    }

    @Bean
    ClientRegistrationRepository workbenchClientRegistrationRepository(final WorkbenchOidcProperties properties) {
        ClientAuthenticationMethod method = "basic".equals(properties.getClientAuthMethod())
                ? ClientAuthenticationMethod.CLIENT_SECRET_BASIC : ClientAuthenticationMethod.CLIENT_SECRET_POST;
        ClientRegistration registration = ClientRegistration.withRegistrationId("workbench")
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .clientAuthenticationMethod(method)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                // 保留 OIDC 注册中的既有公网回调，反向代理再重写到 Java 内部路径。
                .redirectUri(properties.getRedirectUri())
                .scope(properties.getScopes().toArray(String[]::new))
                .authorizationUri(properties.getAuthorizationEndpoint())
                .tokenUri(properties.getTokenEndpoint())
                .jwkSetUri(properties.getJwksUri())
                .issuerUri(properties.getIssuer())
                .userNameAttributeName("sub")
                .clientName("Workbench")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    TransactionTemplate workbenchTransactionTemplate(final PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}
