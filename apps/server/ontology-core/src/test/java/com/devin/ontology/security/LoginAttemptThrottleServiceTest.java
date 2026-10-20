package com.devin.ontology.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 验证限流在数据库中的原子边界和 HTTP 拒绝响应，不连接真实数据库。 */
class LoginAttemptThrottleServiceTest {
    @Test
    void coolingAccountIsBlockedAndExpiredCooldownDoesNotPermanentlyLockIt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LoginAttemptThrottleService service = service(jdbc);
        whenBlocked(jdbc, true);
        assertTrue(service.isBlocked("Alice", "127.0.0.1"));

        whenBlocked(jdbc, false);
        assertFalse(service.isBlocked("Alice", "127.0.0.1"));
    }

    @Test
    void concurrentFailuresIssueAtomicUpsertsForBothIndependentBuckets() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LoginAttemptThrottleService service = service(jdbc);
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    service.recordFailure("Alice", "127.0.0.1");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(threads * 2)).update(sql.capture(), parameters.capture());
        assertTrue(sql.getAllValues().stream().allMatch(statement -> statement.contains("ON CONFLICT")
                && statement.contains("failure_count + 1") && statement.contains("cooldown_until")));
        assertTrue(parameters.getAllValues().stream().allMatch(args ->
                args[1] instanceof byte[] hash && hash.length == 32
                        && Arrays.stream(args).noneMatch(value -> "Alice".equals(value)
                                || "127.0.0.1".equals(value) || "password".equals(value))));
    }

    @Test
    void blockedLoginReturnsGeneric429WithoutReflectingSubmittedCredentials() throws Exception {
        LoginAttemptThrottleService throttle = mock(LoginAttemptThrottleService.class);
        when(throttle.isBlocked("unknown-user", "192.0.2.15")).thenReturn(true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LoginEndpoint())
                .addFilters(new LoginRateLimitFilter(throttle, new ObjectMapper())).build();

        mvc.perform(post("/auth/login").param("username", "unknown-user")
                        .param("password", "must-never-be-reflected").with(request -> {
                            request.setRemoteAddr("192.0.2.15");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"error\":\"too_many_attempts\"}"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("must-never-be-reflected"))));
    }

    private LoginAttemptThrottleService service(final JdbcTemplate jdbc) {
        return new LoginAttemptThrottleService(jdbc, 10, 30, Duration.ofMinutes(5), Duration.ofMinutes(5));
    }

    private void whenBlocked(final JdbcTemplate jdbc, final boolean blocked) {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(blocked);
    }

    @RestController
    static class LoginEndpoint {
        @PostMapping("/auth/login")
        void login() {
            throw new AssertionError("limiter must stop the request before authentication");
        }
    }
}
