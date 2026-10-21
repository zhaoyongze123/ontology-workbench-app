package com.devin.ontology.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 持久化账号和来源地址的独立登录失败桶，数据库 upsert 保证并发失败不会丢计数。 */
@Service
public class LoginAttemptThrottleService {
    private static final String ACCOUNT = "ACCOUNT";
    private static final String IP = "IP";
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final int accountLimit;
    private final int ipLimit;
    private final Duration window;
    private final Duration cooldown;
    private final AtomicLong nextCleanupAtMillis = new AtomicLong();

    public LoginAttemptThrottleService(final JdbcTemplate jdbcTemplate,
            @Value("${ontology.security.login-throttle.account-limit:10}") final int accountLimit,
            @Value("${ontology.security.login-throttle.ip-limit:30}") final int ipLimit,
            @Value("${ontology.security.login-throttle.window:5m}") final Duration window,
            @Value("${ontology.security.login-throttle.cooldown:5m}") final Duration cooldown) {
        if (accountLimit < 1 || ipLimit < 1 || window.toSeconds() < 1 || cooldown.toSeconds() < 1) {
            throw new IllegalArgumentException("登录限流参数必须为正数");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.accountLimit = accountLimit;
        this.ipLimit = ipLimit;
        this.window = window;
        this.cooldown = cooldown;
    }

    /** 账号 key 保持与身份仓储一致的精确大小写语义，不做隐式 lower-case。 */
    public boolean isBlocked(final String username, final String remoteAddr) {
        cleanupExpiredBucketsOccasionally();
        Boolean blocked = jdbcTemplate.queryForObject("SELECT "
                        + "EXISTS (SELECT 1 FROM ontology.og_login_attempt_bucket "
                        + "WHERE bucket_type = 'ACCOUNT' AND key_hash = ? AND cooldown_until > NOW()) "
                        + "OR EXISTS (SELECT 1 FROM ontology.og_login_attempt_bucket "
                        + "WHERE bucket_type = 'IP' AND key_hash = ? AND cooldown_until > NOW())",
                Boolean.class, digest(username), digest(remoteAddr));
        return Boolean.TRUE.equals(blocked);
    }

    /** 给 429 Retry-After 使用与限流桶相同的冷却配置，避免提示时间和真实策略脱节。 */
    public long retryAfterSeconds() {
        return cooldown.toSeconds();
    }

    /** 一次认证失败同时原子更新账号桶和 IP 桶，两个写入要么一起成功，要么一起回滚。 */
    @Transactional
    public void recordFailure(final String username, final String remoteAddr) {
        upsertFailure(ACCOUNT, digest(username), accountLimit);
        upsertFailure(IP, digest(remoteAddr), ipLimit);
    }

    /** 成功登录只清理账号桶；IP 桶不因成功而重置，以免多人共用来源绕过 IP 限制。 */
    @Transactional
    public void clearAccountFailures(final String username) {
        jdbcTemplate.update("DELETE FROM ontology.og_login_attempt_bucket "
                + "WHERE bucket_type = 'ACCOUNT' AND key_hash = ?", digest(username));
    }

    private void upsertFailure(final String type, final byte[] hash, final int limit) {
        long windowSeconds = window.toSeconds();
        long cooldownSeconds = cooldown.toSeconds();
        jdbcTemplate.update("INSERT INTO ontology.og_login_attempt_bucket "
                        + "(bucket_type, key_hash, failure_count, window_started_at, cooldown_until, updated_at) "
                        + "VALUES (?, ?, 1, NOW(), CASE WHEN ? <= 1 "
                        + "THEN NOW() + (? * INTERVAL '1 second') ELSE NULL END, NOW()) "
                        + "ON CONFLICT (bucket_type, key_hash) DO UPDATE SET "
                        + "failure_count = CASE WHEN ontology.og_login_attempt_bucket.window_started_at "
                        + "<= NOW() - (? * INTERVAL '1 second') THEN 1 "
                        + "ELSE ontology.og_login_attempt_bucket.failure_count + 1 END, "
                        + "window_started_at = CASE WHEN ontology.og_login_attempt_bucket.window_started_at "
                        + "<= NOW() - (? * INTERVAL '1 second') THEN NOW() "
                        + "ELSE ontology.og_login_attempt_bucket.window_started_at END, "
                        + "cooldown_until = CASE "
                        + "WHEN ontology.og_login_attempt_bucket.cooldown_until > NOW() "
                        + "THEN ontology.og_login_attempt_bucket.cooldown_until "
                        + "WHEN ontology.og_login_attempt_bucket.window_started_at "
                        + "<= NOW() - (? * INTERVAL '1 second') AND ? <= 1 "
                        + "THEN NOW() + (? * INTERVAL '1 second') "
                        + "WHEN ontology.og_login_attempt_bucket.window_started_at "
                        + "<= NOW() - (? * INTERVAL '1 second') THEN NULL "
                        + "WHEN ontology.og_login_attempt_bucket.failure_count + 1 >= ? "
                        + "THEN NOW() + (? * INTERVAL '1 second') ELSE NULL END, "
                        + "updated_at = NOW()",
                type, hash, limit, cooldownSeconds, windowSeconds, windowSeconds, windowSeconds, limit, cooldownSeconds,
                windowSeconds, limit, cooldownSeconds);
    }

    /** 低频、有上限地清理已越过窗口及冷却期的行，避免每次登录扫描全表。 */
    private void cleanupExpiredBucketsOccasionally() {
        long now = System.currentTimeMillis();
        long next = nextCleanupAtMillis.get();
        if (now < next || !nextCleanupAtMillis.compareAndSet(next, now + Duration.ofMinutes(10).toMillis())) {
            return;
        }
        long ageSeconds = Math.max(60, window.plus(cooldown).toSeconds());
        jdbcTemplate.update("WITH expired AS (SELECT ctid FROM ontology.og_login_attempt_bucket "
                        + "WHERE updated_at < NOW() - (? * INTERVAL '1 second') "
                        + "ORDER BY updated_at LIMIT ? FOR UPDATE SKIP LOCKED) "
                        + "DELETE FROM ontology.og_login_attempt_bucket b USING expired e WHERE b.ctid = e.ctid",
                ageSeconds, CLEANUP_BATCH_SIZE);
    }

    private byte[] digest(final String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 未提供 SHA-256", impossible);
        }
    }
}
