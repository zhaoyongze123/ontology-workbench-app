package com.devin.ontology.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.OntologyServerApplication;
import com.devin.ontology.security.LoginUserPrincipal;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 只写入显式隔离的 ontology_multiuser_dev_20261020 测试副本，逐项清除本用例创建的身份。 */
@SpringBootTest(classes = OntologyServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("multiuser")
@EnabledIfEnvironmentVariable(named = "ONTOLOGY_IT_JDBC_URL", matches = ".*ontology_multiuser_dev_20261020.*")
class AccountLifecycleDatabaseIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountLifecycleService accounts;
    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String adminId;
    private String targetId;
    private String secondAdminId;

    @DynamicPropertySource
    static void databaseProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AccountLifecycleDatabaseIntegrationTest::databaseUrl);
        registry.add("spring.datasource.username", () -> required("JDBC_USER"));
        registry.add("spring.datasource.password", () -> required("JDBC_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.main.web-application-type", () -> "none");
        registry.add("graph.database.type", () -> "age");
        registry.add("graph.database.age.preloaded", () -> "true");
        registry.add("ontology.workbench.enabled", () -> "false");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (targetId != null) jdbc.update("DELETE FROM ontology.og_user WHERE id = ?", targetId);
        if (secondAdminId != null) jdbc.update("DELETE FROM ontology.og_user WHERE id = ?", secondAdminId);
        if (adminId != null) jdbc.update("DELETE FROM ontology.og_user WHERE id = ?", adminId);
    }

    @Test
    void activationDoesNotUndoAdminDisableAndResetConsumesOnceAndRevokesAuthVersion() {
        adminId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ontology.og_user(id, username, display_name, system_admin, enabled, "
                        + "password_activated, auth_version) VALUES (?, ?, 'IT Admin', TRUE, TRUE, TRUE, 0)",
                adminId, "it_admin_" + UUID.randomUUID());
        var principal = new LoginUserPrincipal(adminId, "it-admin", "IT Admin", true, 0,
                System.currentTimeMillis());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));

        var created = accounts.createPendingUser(new AccountLifecycleService.CreateAccountRequest(
                "it_user_" + UUID.randomUUID(), "IT User"));
        targetId = created.user().id();
        assertThat(created.activationUrl()).contains("/ontology-management/account/activate#token=");
        String oldActivationToken = fragmentToken(created.activationUrl());
        String activationToken = fragmentToken(accounts.issueActivationLink(targetId));
        assertThat(accounts.inspectActionToken(oldActivationToken).valid()).isFalse();
        assertThat(accounts.inspectActionToken(activationToken).valid()).isTrue();
        assertThat(accounts.inspectActionToken(activationToken).valid()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_account_action_token "
                + "WHERE token_digest = ?", Integer.class, activationToken)).isZero();

        accounts.setEnabled(targetId, false);
        accounts.activate(activationToken, "correct horse battery staple");
        assertThat(jdbc.queryForObject("SELECT enabled FROM ontology.og_user WHERE id = ?", Boolean.class, targetId))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT password_activated FROM ontology.og_user WHERE id = ?",
                Boolean.class, targetId)).isTrue();
        assertThat(accounts.inspectActionToken(activationToken).valid()).isFalse();

        accounts.setEnabled(targetId, true);
        long versionBeforeRequest = authVersion(targetId);
        String resetToken = fragmentToken(accounts.issuePasswordResetLink(targetId));
        assertThat(authVersion(targetId)).isEqualTo(versionBeforeRequest);
        assertThat(accounts.inspectActionToken(resetToken).valid()).isTrue();
        assertThatThrownBy(() -> accounts.activate(resetToken, "incorrect purpose password"))
                .isInstanceOf(OntologyApiException.class);
        assertThat(accounts.inspectActionToken(resetToken).valid()).isTrue();
        assertThat(passwordEncoder.matches("correct horse battery staple", passwordHash(targetId))).isTrue();
        accounts.resetPassword(resetToken, "another correct horse battery");
        assertThat(authVersion(targetId)).isEqualTo(versionBeforeRequest + 1);
        assertThat(accounts.inspectActionToken(resetToken).valid()).isFalse();
        assertThat(passwordEncoder.matches("correct horse battery staple", passwordHash(targetId))).isFalse();
        assertThatThrownBy(() -> accounts.resetPassword(resetToken, "third correct horse battery"))
                .isInstanceOf(OntologyApiException.class);
    }

    @Test
    void anonymousPasswordActionPostIsCsrfExemptAndNeverCacheable() throws Exception {
        mvc.perform(post("/auth/account-actions/inspect")
                        .contentType("application/json")
                        .content("{\"token\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void malformedActionBodyAndGetMutationAreClientErrors() throws Exception {
        mvc.perform(post("/auth/activate").contentType("application/json").content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error").value("invalid_request"));
        mvc.perform(get("/auth/activate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error").value("method_not_allowed"));
    }

    @Test
    void lastAdministratorHttpDisableKeepsTheLegacyConflictBody() throws Exception {
        adminId = UUID.randomUUID().toString();
        insertAdmin(adminId, "it_last_admin_http_");
        String username = jdbc.queryForObject("SELECT username FROM ontology.og_user WHERE id = ?",
                String.class, adminId);
        var principal = new LoginUserPrincipal(adminId, username, "IT Admin", true, 0,
                System.currentTimeMillis());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, java.util.List.of());

        mvc.perform(patch("/admin/users/{userId}/enabled", adminId)
                        .with(authentication(authentication))
                        .with(csrf())
                        .contentType("application/json")
                        .accept("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"conflict\"}"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void nonSystemAdminCannotListOrManageAccounts() {
        prepareAdmin();
        var created = accounts.createPendingUser(new AccountLifecycleService.CreateAccountRequest(
                "it_nonadmin_" + UUID.randomUUID(), "IT Nonadmin"));
        targetId = created.user().id();
        var principal = new LoginUserPrincipal(targetId, created.user().username(), "IT Nonadmin", false,
                0, System.currentTimeMillis());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accounts.listUsers(0, 20))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void expiredAndReplacedActionTokensCannotBeUsed() {
        prepareAdmin();
        var created = accounts.createPendingUser(new AccountLifecycleService.CreateAccountRequest(
                "it_expired_" + UUID.randomUUID(), "IT Expired"));
        targetId = created.user().id();
        String expiredToken = fragmentToken(accounts.issuePasswordResetLink(targetId));
        jdbc.update("UPDATE ontology.og_account_action_token SET expires_at = NOW() - INTERVAL '1 second' "
                + "WHERE token_digest = ?", tokenDigest(expiredToken));
        assertThat(accounts.inspectActionToken(expiredToken).valid()).isFalse();
        assertThatThrownBy(() -> accounts.resetPassword(expiredToken, "valid length password one"))
                .isInstanceOf(OntologyApiException.class);

        String replacedToken = fragmentToken(accounts.issuePasswordResetLink(targetId));
        String currentToken = fragmentToken(accounts.issuePasswordResetLink(targetId));
        assertThat(accounts.inspectActionToken(replacedToken).valid()).isFalse();
        assertThat(accounts.inspectActionToken(currentToken).valid()).isTrue();
    }

    @Test
    void concurrentActivationConsumesExactlyOneToken() throws Exception {
        prepareAdmin();
        var created = accounts.createPendingUser(new AccountLifecycleService.CreateAccountRequest(
                "it_race_" + UUID.randomUUID(), "IT Race"));
        targetId = created.user().id();
        String token = fragmentToken(created.activationUrl());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> activateAsConcurrentCaller(token, start));
            Future<Boolean> second = pool.submit(() -> activateAsConcurrentCaller(token, start));
            start.countDown();
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_password_credential WHERE user_id = ?",
                Integer.class, targetId)).isEqualTo(1);
        assertThat(accounts.inspectActionToken(token).valid()).isFalse();
    }

    @Test
    void concurrentAdminDisablesCannotDisableBothRemainingAdmins() throws Exception {
        adminId = UUID.randomUUID().toString();
        secondAdminId = UUID.randomUUID().toString();
        insertAdmin(adminId, "it_admin_a_");
        insertAdmin(secondAdminId, "it_admin_b_");
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> disableAs(adminId, secondAdminId, start));
            var second = pool.submit(() -> disableAs(secondAdminId, adminId, start));
            start.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }
        Integer enabledAmongPair = jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_user "
                + "WHERE id IN (?, ?) AND enabled = TRUE", Integer.class, adminId, secondAdminId);
        assertThat(enabledAmongPair).isEqualTo(1);
    }

    private long authVersion(final String userId) {
        return jdbc.queryForObject("SELECT auth_version FROM ontology.og_user WHERE id = ?", Long.class, userId);
    }

    private String passwordHash(final String userId) {
        return jdbc.queryForObject("SELECT password_hash FROM ontology.og_password_credential WHERE user_id = ?",
                String.class, userId);
    }

    private void insertAdmin(final String id, final String usernamePrefix) {
        jdbc.update("INSERT INTO ontology.og_user(id, username, display_name, system_admin, enabled, "
                        + "password_activated, auth_version) VALUES (?, ?, 'IT Admin', TRUE, TRUE, TRUE, 0)",
                id, usernamePrefix + UUID.randomUUID());
    }

    private void prepareAdmin() {
        adminId = UUID.randomUUID().toString();
        insertAdmin(adminId, "it_admin_action_");
        var principal = new LoginUserPrincipal(adminId, "it-admin", "IT Admin", true, 0,
                System.currentTimeMillis());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));
    }

    private boolean activateAsConcurrentCaller(final String token, final CountDownLatch start) {
        try {
            start.await();
            accounts.activate(token, "race correct horse battery stable");
            return true;
        } catch (OntologyApiException expected) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("IT activation race interrupted", interrupted);
        }
    }

    private void disableAs(final String actorId, final String targetUserId, final CountDownLatch start) {
        try {
            start.await();
            String username = jdbc.queryForObject("SELECT username FROM ontology.og_user WHERE id = ?",
                    String.class, actorId);
            var principal = new LoginUserPrincipal(actorId, username, "IT Admin", true, 0,
                    System.currentTimeMillis());
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));
            try {
                accounts.setEnabled(targetUserId, false);
            } catch (org.springframework.security.access.AccessDeniedException ignored) {
                // 获胜事务停用另一位管理员后，败方必须按当前数据库身份重新授权并被拒绝。
            } catch (org.springframework.security.core.AuthenticationException ignored) {
                // 重新读取到被禁用身份时，当前身份边界拒绝继续管理。
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("IT concurrency test interrupted", interrupted);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static String fragmentToken(final String link) {
        String fragment = URI.create(link).getFragment();
        return fragment == null ? "" : fragment.substring("token=".length());
    }

    private static String tokenDigest(final String token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String databaseUrl() {
        String url = required("ONTOLOGY_IT_JDBC_URL");
        if (!url.contains("ontology_multiuser_dev_20261020")) {
            throw new IllegalStateException("IT JDBC URL must target the isolated clone");
        }
        return url;
    }

    private static String required(final String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be configured");
        return value;
    }
}
