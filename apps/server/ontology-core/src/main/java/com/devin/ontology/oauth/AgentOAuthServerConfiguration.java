package com.devin.ontology.oauth;

import com.devin.ontology.identity.IdentityRepository;
import com.devin.ontology.security.RequestCorrelationFilter;
import com.devin.ontology.security.PublicOrigin;
import com.devin.ontology.security.SecurityAuditRecorder;
import com.devin.ontology.security.TrustedClientAddressResolver;
import com.devin.ontology.security.AuditActorCaptureFilter;
import com.devin.ontology.security.SessionValidityFilter;
import com.devin.ontology.security.OAuthProtocolRateLimitFilter;
import com.devin.ontology.security.OAuthProtocolRequestLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import java.util.Arrays;
import java.util.Set;
import java.time.Duration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.transaction.PlatformTransactionManager;

/** OAuth 授权服务器只负责 App 授权协议；业务 API 的 Bearer 校验由独立资源链负责。 */
@Configuration
@Profile("multiuser")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AgentOAuthServerConfiguration {
    @Bean
    AuthorizationServerSettings agentAuthorizationServerSettings(
            @Value("${ontology.security.public-origin:https://ontology.invalid}") final String publicOrigin,
            @Value("${server.servlet.context-path:/api/v1}") final String servletContextPath) {
        String origin = PublicOrigin.parse(publicOrigin);
        String contextPath = servletContextPath == null ? "" : servletContextPath.trim();
        if (contextPath.isBlank() || "/".equals(contextPath)) {
            contextPath = "";
        } else {
            if (!contextPath.startsWith("/")) contextPath = "/" + contextPath;
            contextPath = contextPath.replaceAll("/+$", "");
        }
        // issuer 是公开服务基址，包含 Servlet context path；下面各 endpoint 仍是 context 内的相对路径。
        String issuer = origin + contextPath;
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
    FilterRegistrationBean<OAuthTokenTransactionFilter> agentOAuthTokenTransactionFilter(
            final PlatformTransactionManager transactionManager) {
        FilterRegistrationBean<OAuthTokenTransactionFilter> registration =
                new FilterRegistrationBean<>(new OAuthTokenTransactionFilter(transactionManager));
        registration.setName("agentOAuthTokenTransactionFilter");
        registration.addUrlPatterns("/oidc/app/token", "/oidc/app/revoke");
        // 在 Spring Security 之前包裹完整 token/撤销请求，令数据库行锁覆盖读取、校验与授权保存。
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain agentOAuthServerFilterChain(final HttpSecurity http,
            final RegisteredClientRepository clients, final OAuth2AuthorizationService authorizations,
            final OAuth2AuthorizationConsentService consents,
            final IdentityRepository identities,
            final AuthorizationServerSettings settings,
            final OpaqueOAuth2TokenGenerator tokenGenerator,
            final ObjectMapper mapper,
            final TrustedClientAddressResolver addressResolver,
            final SecurityAuditRecorder auditRecorder,
            final OAuthProtocolRequestLimiter requestLimiter,
            @Value("${ontology.security.oauth-throttle.cooldown:1m}") final Duration throttleCooldown)
            throws Exception {
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
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    // 仅匿名浏览器 GET authorize 转到产品登录页；协议 POST 与 token/revoke 仍返回 401。
                    String authorizePath = request.getContextPath() + "/oidc/app/authorize";
                    if ("GET".equals(request.getMethod()) && authorizePath.equals(request.getRequestURI())) {
                        String original = authorizePath + (request.getQueryString() == null
                                ? "" : "?" + request.getQueryString());
                        String login = "/ontology-management/login?oauth_return="
                                + URLEncoder.encode(original, StandardCharsets.UTF_8);
                        response.sendRedirect(login);
                    } else {
                        response.sendError(HttpStatus.UNAUTHORIZED.value());
                    }
                }))
                .addFilterBefore(new RequestCorrelationFilter(addressResolver, auditRecorder),
                        SecurityContextHolderFilter.class)
                .addFilterAfter(new OAuthProtocolRateLimitFilter(requestLimiter, addressResolver, mapper,
                        throttleCooldown.toSeconds()), RequestCorrelationFilter.class)
                // OAuth 授权复用登录会话，但仍逐请求核验空闲/绝对时限和账号 authVersion。
                .addFilterAfter(new SessionValidityFilter(identities), SecurityContextHolderFilter.class)
                .addFilterAfter(new AuditActorCaptureFilter(), AnonymousAuthenticationFilter.class)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain agentBearerApiFilterChain(final HttpSecurity http,
            final AgentAccessTokenService tokenService, final ObjectMapper mapper,
            final TrustedClientAddressResolver resolver,
            final SecurityAuditRecorder auditRecorder) throws Exception {
        org.springframework.security.web.util.matcher.RequestMatcher bearerApi = request -> {
            String header = request.getHeader("Authorization");
            // 业务 API 一旦显式带 Authorization 就进入无状态链，错误凭证不得回退到浏览器 Cookie。
            if (header == null || header.isBlank()) return false;
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return "/oidc/app/userinfo".equals(path) || path.startsWith("/organization/")
                    || path.startsWith("/projects/") || path.startsWith("/project-invitations/")
                    || "/users/search".equals(path)
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
                .addFilterBefore(new RequestCorrelationFilter(resolver, auditRecorder), SecurityContextHolderFilter.class)
                .addFilterBefore(new AgentBearerAuthenticationFilter(tokenService, mapper), AnonymousAuthenticationFilter.class)
                .addFilterAfter(new AuditActorCaptureFilter(), AnonymousAuthenticationFilter.class)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
