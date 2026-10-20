package com.devin.ontology.security;

import com.devin.ontology.identity.IdentityRepository;
import com.devin.ontology.oidc.WorkbenchOidcConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/** multiuser 配置采用数据库会话和显式路由白名单，未列出的业务 API 一律拒绝。 */
@Configuration
@Profile("multiuser")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableJdbcHttpSession(tableName = "ontology.spring_session", maxInactiveIntervalInSeconds = 1800)
public class MultiuserSecurityConfiguration {
    @Bean
    CookieSerializer sessionCookieSerializer(
            @Value("${ontology.security.cookie-secure:true}") final boolean secure,
            @Value("${ontology.security.cookie-same-site:None}") final String sameSite) {
        if (!Set.of("None", "Lax", "Strict").contains(sameSite) || (!secure && "None".equals(sameSite))) {
            throw new IllegalArgumentException("跨站会话必须使用 Secure Cookie；本机 HTTP 副本请显式使用 Lax");
        }
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("ONTOLOGY_SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite(sameSite);
        serializer.setCookiePath("/");
        // 不设置 domain，浏览器按 host-only Cookie 处理。
        return serializer;
    }

    @Bean
    DatabaseAuthenticationProvider databaseAuthenticationProvider(final IdentityRepository identities,
            final PasswordEncoder passwordEncoder) {
        return new DatabaseAuthenticationProvider(identities, passwordEncoder);
    }

    @Bean
    SecurityFilterChain multiuserSecurityFilterChain(final HttpSecurity http,
            final IdentityRepository identities, final ObjectMapper objectMapper,
            final DatabaseAuthenticationProvider databaseAuthenticationProvider,
            final ObjectProvider<WorkbenchOidcConfigurer> workbenchConfigurer,
            final LoginAttemptThrottleService throttleService,
            @Value("${ontology.security.trusted-origins:}") final String origins) throws Exception {
        HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
        csrfRepository.setHeaderName("X-CSRF-TOKEN");
        Set<String> trustedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());

        http.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionAuthenticationStrategy(new ChangeSessionIdAuthenticationStrategy()))
                // 保留框架构建的管理器，使本地密码与 OIDC provider 共存。
                .authenticationProvider(databaseAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/csrf", "/auth/options").permitAll()
                        .requestMatchers("/oauth2/authorization/workbench", "/oidc/callback").permitAll()
                        .requestMatchers("/auth/me", "/auth/logout", "/auth/logout-all",
                                "/auth/workbench/bind").authenticated()
                        .requestMatchers("/organization/**", "/graph/**", "/node/**", "/relationship/**",
                                "/cypher/query", "/projects/**", "/project-invitations/**", "/users/search")
                        .authenticated()
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginProcessingUrl("/auth/login")
                        .successHandler((request, response, authentication) -> {
                            LoginUserPrincipal principal = (LoginUserPrincipal) authentication.getPrincipal();
                            try {
                                throttleService.clearAccountFailures(request.getParameter("username"));
                            } catch (RuntimeException unavailable) {
                                request.getSession(false).invalidate();
                                org.springframework.security.core.context.SecurityContextHolder.clearContext();
                                jsonError(response, objectMapper, 503, "authentication_unavailable");
                                return;
                            }
                            request.getSession(true).setAttribute(SessionValidityFilter.LAST_REAL_ACTIVITY,
                                    System.currentTimeMillis());
                            response.setStatus(200);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), Map.of("user", Map.of(
                                    "id", principal.userId(), "username", principal.username(),
                                    "displayName", principal.displayName(), "systemAdmin", principal.systemAdmin())));
                        })
                        .failureHandler((request, response, exception) -> {
                            try {
                                if (exception instanceof org.springframework.security.authentication.BadCredentialsException) {
                                    throttleService.recordFailure(request.getParameter("username"),
                                            request.getRemoteAddr());
                                }
                                jsonError(response, objectMapper, 401, "invalid_credentials");
                            } catch (RuntimeException unavailable) {
                                jsonError(response, objectMapper, 503, "authentication_unavailable");
                            }
                        }))
                .logout(logout -> logout.logoutUrl("/auth/logout").invalidateHttpSession(true)
                        .clearAuthentication(true).logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), Map.of("status", "logged_out"));
                        }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> jsonError(response,
                                objectMapper, 401, "unauthorized"))
                        .accessDeniedHandler((request, response, exception) -> jsonError(response,
                                objectMapper, 403, "forbidden")))
                .addFilterBefore(new TrustedOriginFilter(trustedOrigins), CsrfFilter.class)
                .addFilterBefore(new LoginRateLimitFilter(throttleService, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new SessionValidityFilter(identities),
                        org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .requestCache(AbstractHttpConfigurer::disable);
        WorkbenchOidcConfigurer oidc = workbenchConfigurer.getIfAvailable();
        if (oidc != null) {
            oidc.configure(http);
        }
        return http.build();
    }

    private static void jsonError(final jakarta.servlet.http.HttpServletResponse response,
            final ObjectMapper mapper, final int status, final String code) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("error", code));
    }
}
