package com.devin.ontology.oauth;

import com.devin.ontology.security.RequestCorrelationFilter;
import com.devin.ontology.security.TrustedClientAddressResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.web.OAuth2TokenEndpointFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.transaction.PlatformTransactionManager;

/** OAuth 授权服务器只负责 App 授权协议；业务 API 的 Bearer 校验由独立资源链负责。 */
@Configuration
@Profile("multiuser")
public class AgentOAuthServerConfiguration {
    @Bean
    AuthorizationServerSettings agentAuthorizationServerSettings(
            @Value("${ontology.security.public-origin:https://ontology.invalid}") final String publicOrigin) {
        String issuer = publicOrigin.replaceAll("/+$", "");
        return AuthorizationServerSettings.builder().issuer(issuer)
                .authorizationEndpoint("/oidc/app/authorize")
                .tokenEndpoint("/oidc/app/token")
                .tokenRevocationEndpoint("/oidc/app/revoke")
                .build();
    }

    @Bean
    OpaqueOAuth2TokenGenerator agentOAuth2TokenGenerator() {
        return new OpaqueOAuth2TokenGenerator();
    }

    @Bean
    @Order(1)
    SecurityFilterChain agentOAuthServerFilterChain(final HttpSecurity http,
            final RegisteredClientRepository clients, final OAuth2AuthorizationService authorizations,
            final OAuth2AuthorizationConsentService consents,
            final AuthorizationServerSettings settings,
            final OpaqueOAuth2TokenGenerator tokenGenerator,
            final PlatformTransactionManager transactionManager) throws Exception {
        OAuth2AuthorizationServerConfigurer server = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(server.getEndpointsMatcher())
                .with(server, configurer -> configurer
                        .registeredClientRepository(clients)
                        .authorizationService(authorizations)
                        .authorizationConsentService(consents)
                        .authorizationServerSettings(settings)
                        .tokenGenerator(tokenGenerator)
                        .authorizationEndpoint(endpoint -> endpoint.consentPage("/oidc/app/consent")))
                // 只有服务端 token/revoke 请求不使用浏览器 Cookie；浏览器授权确认仍受 CSRF 保护。
                .csrf(csrf -> csrf.ignoringRequestMatchers("/oidc/app/token", "/oidc/app/revoke"))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new OAuthTokenTransactionFilter(transactionManager), OAuth2TokenEndpointFilter.class)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain agentBearerApiFilterChain(final HttpSecurity http,
            final AgentAccessTokenService tokenService, final ObjectMapper mapper,
            @Value("${ontology.security.trusted-proxies:}") final String proxyAddresses) throws Exception {
        Set<String> trustedProxies = Arrays.stream(proxyAddresses.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toSet());
        TrustedClientAddressResolver resolver = new TrustedClientAddressResolver(trustedProxies);
        org.springframework.security.web.util.matcher.RequestMatcher bearerApi = request -> {
            String header = request.getHeader("Authorization");
            // 业务 API 一旦显式带 Authorization 就进入无状态链，错误凭证不得回退到浏览器 Cookie。
            if (header == null || header.isBlank()) return false;
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return "/oidc/app/userinfo".equals(path) || path.startsWith("/organization/")
                    || path.startsWith("/graph/") || path.startsWith("/node/")
                    || path.startsWith("/relationship/") || path.startsWith("/cypher/");
        };
        http.securityMatcher(bearerApi)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .authorizeHttpRequests(auth -> auth.anyRequest().access((authentication, context) ->
                        new AuthorizationDecision(new AgentScopePolicy()
                                .isAllowed(authentication.get(), context.getRequest()))))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            mapper.writeValue(response.getWriter(), java.util.Map.of("error", "insufficient_scope"));
                        }))
                .addFilterBefore(new RequestCorrelationFilter(resolver), SecurityContextHolderFilter.class)
                .addFilterBefore(new AgentBearerAuthenticationFilter(tokenService, mapper), AnonymousAuthenticationFilter.class)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
