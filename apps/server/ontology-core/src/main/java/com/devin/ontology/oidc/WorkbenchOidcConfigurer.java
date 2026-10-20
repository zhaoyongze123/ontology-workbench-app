package com.devin.ontology.oidc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.stereotype.Component;

/** 供主安全配置显式调用的 OIDC 接入器；不自行创建或替代 SecurityFilterChain。 */
@Component
@ConditionalOnProperty(prefix = "ontology.workbench", name = "enabled", havingValue = "true")
public class WorkbenchOidcConfigurer {
    public static final String AUTHORIZATION_ENTRY = "/oauth2/authorization/workbench";
    /** Spring Security matcher 按 servlet context 内路径匹配；公网 URI 仍是 /oidc/callback。 */
    public static final String CALLBACK_PATH = "/oidc/callback";
    private final ClientRegistrationRepository registrations;
    private final AuthenticationSuccessHandler successHandler;
    private final AuthenticationFailureHandler failureHandler;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final OidcUserService oidcUserService;

    public WorkbenchOidcConfigurer(final ClientRegistrationRepository registrations,
            final AuthenticationSuccessHandler workbenchOidcSuccessHandler,
            final AuthenticationFailureHandler workbenchOidcFailureHandler,
            final OAuth2AuthorizedClientRepository workbenchAuthorizedClientRepository,
            final OidcUserService oidcUserService) {
        this.registrations = registrations;
        this.successHandler = workbenchOidcSuccessHandler;
        this.failureHandler = workbenchOidcFailureHandler;
        this.authorizedClientRepository = workbenchAuthorizedClientRepository;
        this.oidcUserService = oidcUserService;
    }

    /** 调用方需将 AUTHORIZATION_ENTRY 与 CALLBACK_PATH 加入 permitAll 白名单。 */
    public void configure(final HttpSecurity http) throws Exception {
        OAuth2AuthorizationRequestResolver resolver = pkceResolver(registrations);
        http.oauth2Login(oauth -> oauth
                // 先阻止认证过滤器把含原始 ID Token 的 OidcUser 写进 session；成功处理器只保存内部 principal。
                .securityContextRepository(new NullSecurityContextRepository())
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK_PATH))
                .userInfoEndpoint(endpoint -> endpoint.oidcUserService(oidcUserService))
                .authorizedClientRepository(authorizedClientRepository)
                .successHandler(successHandler)
                .failureHandler(failureHandler));
    }

    static OAuth2AuthorizationRequestResolver pkceResolver(
            final ClientRegistrationRepository registrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

}
