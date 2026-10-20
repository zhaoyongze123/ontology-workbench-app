package com.devin.ontology.oidc;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.devin.ontology.security.LoginUserPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.FilterChainProxy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 实际构建 OIDC 启用时的过滤器链，确认 OIDC 与本地密码使用各自的会话持久化边界。 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = WorkbenchOidcSecurityFilterChainTest.TestSecurityConfiguration.class)
class WorkbenchOidcSecurityFilterChainTest {
    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void oidcFilterSkipsRawIdentityPersistenceWhilePasswordLoginKeepsSessionPersistence() throws Exception {
        OAuth2LoginAuthenticationFilter oidcFilter = securityFilterChain.getFilters().stream()
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast)
                .findFirst().orElseThrow();
        UsernamePasswordAuthenticationFilter passwordFilter = securityFilterChain.getFilters().stream()
                .filter(UsernamePasswordAuthenticationFilter.class::isInstance)
                .map(UsernamePasswordAuthenticationFilter.class::cast)
                .findFirst().orElseThrow();

        assertInstanceOf(NullSecurityContextRepository.class, securityContextRepository(oidcFilter));
        // 框架没有公开 repository getter；反射仅用于回归断言过滤器实例的配置结果。
        assertFalse(securityContextRepository(passwordFilter) instanceof NullSecurityContextRepository);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LoginProbeController())
                .addFilters(new FilterChainProxy(List.of(securityFilterChain)))
                .build();
        var result = mockMvc.perform(post("/auth/login").param("username", "alice")
                        .param("password", "correct-password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        SecurityContext savedContext = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertNotNull(savedContext);
        assertTrue(savedContext.getAuthentication().isAuthenticated());
        LoginUserPrincipal principal = assertInstanceOf(LoginUserPrincipal.class,
                savedContext.getAuthentication().getPrincipal());
        assertEquals("user-1", principal.userId());
        assertNull(savedContext.getAuthentication().getCredentials());
    }

    private static org.springframework.security.web.context.SecurityContextRepository securityContextRepository(
            final AbstractAuthenticationProcessingFilter filter) throws ReflectiveOperationException {
        var field = AbstractAuthenticationProcessingFilter.class.getDeclaredField("securityContextRepository");
        field.setAccessible(true);
        return (org.springframework.security.web.context.SecurityContextRepository) field.get(filter);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {
        @Bean
        ClientRegistrationRepository clientRegistrations() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("workbench")
                    .clientId("test-client").clientSecret("test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("https://gateway.example/oidc/callback")
                    .scope("openid", "profile")
                    .authorizationUri("https://identity.example/authorize")
                    .tokenUri("https://identity.example/token")
                    .jwkSetUri("https://identity.example/jwks")
                    .issuerUri("https://identity.example")
                    .userNameAttributeName("sub").clientName("Workbench").build());
        }

        @Bean
        OAuth2AuthorizedClientRepository authorizedClients() {
            return mock(OAuth2AuthorizedClientRepository.class);
        }

        @Bean
        OidcUserService oidcUserService() {
            return new OidcUserService();
        }

        @Bean
        AuthenticationSuccessHandler oidcSuccessHandler() {
            return (request, response, authentication) -> response.setStatus(204);
        }

        @Bean
        AuthenticationFailureHandler oidcFailureHandler() {
            return (request, response, exception) -> response.setStatus(401);
        }

        @Bean
        WorkbenchOidcConfigurer workbenchOidcConfigurer(
                final ClientRegistrationRepository registrations,
                final AuthenticationSuccessHandler oidcSuccessHandler,
                final AuthenticationFailureHandler oidcFailureHandler,
                final OAuth2AuthorizedClientRepository authorizedClients,
                final OidcUserService oidcUserService) {
            return new WorkbenchOidcConfigurer(registrations, oidcSuccessHandler,
                    oidcFailureHandler, authorizedClients, oidcUserService);
        }

        @Bean
        AuthenticationProvider passwordAuthenticationProvider() {
            return new AuthenticationProvider() {
                @Override
                public Authentication authenticate(final Authentication authentication)
                        throws AuthenticationException {
                    if (!"alice".equals(authentication.getName())
                            || !"correct-password".equals(authentication.getCredentials())) return null;
                    return UsernamePasswordAuthenticationToken.authenticated(
                            new LoginUserPrincipal("user-1", "alice", "Alice", false, 1,
                                    System.currentTimeMillis()), null, List.of());
                }

                @Override
                public boolean supports(final Class<?> authentication) {
                    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
                }
            };
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(final HttpSecurity http,
                final WorkbenchOidcConfigurer oidcConfigurer,
                final AuthenticationProvider passwordAuthenticationProvider) throws Exception {
            http.authenticationProvider(passwordAuthenticationProvider)
                    .csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .formLogin(form -> form.loginProcessingUrl("/auth/login"));
            // 用生产接入器构建真实 OIDC 过滤器配置，身份与网络交互均由本地 fixture 隔离。
            oidcConfigurer.configure(http);
            return http.build();
        }
    }

    @org.springframework.web.bind.annotation.RestController
    static class LoginProbeController {
        @org.springframework.web.bind.annotation.GetMapping("/probe")
        String probe() {
            return "ok";
        }
    }
}
