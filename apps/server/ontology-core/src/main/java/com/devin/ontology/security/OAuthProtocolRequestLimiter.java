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

/** OAuth 协议请求独立于密码失败桶，按受信来源和客户端双维度持久限流。 */
@Service
public class OAuthProtocolRequestLimiter {
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final int sourceLimit;
    private final int clientLimit;
    private final Duration window;
    private final Duration cooldown;
    private final AtomicLong nextCleanupAtMillis = new AtomicLong();

    public OAuthProtocolRequestLimiter(final JdbcTemplate jdbc,
            @Value("${ontology.security.oauth-throttle.source-limit:60}") final int sourceLimit,
            @Value("${ontology.security.oauth-throttle.client-limit:120}") final int clientLimit,
            @Value("${ontology.security.oauth-throttle.window:5m}") final Duration window,
            @Value("${ontology.security.oauth-throttle.cooldown:1m}") final Duration cooldown) {
        if (sourceLimit < 1 || clientLimit < 1 || window.toSeconds() < 1 || cooldown.toSeconds() < 1) {
            throw new IllegalArgumentException("OAuth 限流参数必须为正数");
        }
        this.jdbc = jdbc;
        this.sourceLimit = sourceLimit;
        this.clientLimit = clientLimit;
        this.window = window;
        this.cooldown = cooldown;
    }

    /** 成功和失败协议请求都计入凭证类型桶；不读取或保存授权码、Secret、Cookie、Token。 */
    @Transactional
    public boolean allowRequest(final String credentialType, final String clientId, final String trustedSource) {
        if (!credentialType.equals("OAUTH_AUTHORIZE") && !credentialType.equals("OAUTH_TOKEN")
                && !credentialType.equals("OAUTH_REVOKE") && !credentialType.equals("ACCOUNT_ACTIVATE")
                && !credentialType.equals("ACCOUNT_RESET")) {
            throw new IllegalArgumentException("OAuth 限流凭证类型无效");
        }
        cleanupExpiredBucketsOccasionally();
        boolean sourceBlocked = incrementAndCheck(credentialType, "SOURCE", trustedSource, sourceLimit);
        boolean clientBlocked = clientId == null || clientId.isBlank() ? false
                : incrementAndCheck(credentialType, "CLIENT", clientId, clientLimit);
        return !sourceBlocked && !clientBlocked;
    }

    private boolean incrementAndCheck(final String type, final String dimension,
            final String value, final int limit) {
        long windowSeconds = window.toSeconds();
        long cooldownSeconds = cooldown.toSeconds();
        Boolean blocked = jdbc.queryForObject("INSERT INTO ontology.og_credential_request_bucket "
                        + "(credential_type, bucket_dimension, key_hash, request_count, window_started_at, "
                        + "blocked_until, updated_at) VALUES (?, ?, ?, 1, NOW(), NULL, NOW()) "
                        + "ON CONFLICT (credential_type, bucket_dimension, key_hash) DO UPDATE SET "
                        + "request_count = CASE WHEN ontology.og_credential_request_bucket.blocked_until > NOW() "
                        + "THEN ontology.og_credential_request_bucket.request_count "
                        + "WHEN ontology.og_credential_request_bucket.window_started_at "
                        + "<= NOW() - (? * INTERVAL '1 second') THEN 1 "
                        + "ELSE ontology.og_credential_request_bucket.request_count + 1 END, "
                        + "window_started_at = CASE WHEN ontology.og_credential_request_bucket.window_started_at "
                        + "<= NOW() - (? * INTERVAL '1 second') THEN NOW() "
                        + "ELSE ontology.og_credential_request_bucket.window_started_at END, "
                        + "blocked_until = CASE "
                        + "WHEN ontology.og_credential_request_bucket.blocked_until > NOW() "
                        + "THEN ontology.og_credential_request_bucket.blocked_until "
                        + "WHEN ontology.og_credential_request_bucket.window_started_at "
                        + "<= NOW() - (? * INTERVAL '1 second') THEN NULL "
                        + "WHEN ontology.og_credential_request_bucket.request_count + 1 > ? "
                        + "THEN NOW() + (? * INTERVAL '1 second') ELSE NULL END, updated_at = NOW() "
                        + "RETURNING blocked_until IS NOT NULL AND blocked_until > NOW()",
                Boolean.class, type, dimension, digest(type, dimension, value), windowSeconds,
                windowSeconds, windowSeconds, limit, cooldownSeconds);
        return Boolean.TRUE.equals(blocked);
    }

    /** 节流状态可定期清理；审计事件表不使用该清理逻辑。 */
    private void cleanupExpiredBucketsOccasionally() {
        long now = System.currentTimeMillis();
        long next = nextCleanupAtMillis.get();
        if (now < next || !nextCleanupAtMillis.compareAndSet(next, now + Duration.ofMinutes(10).toMillis())) {
            return;
        }
        long ageSeconds = Math.max(60, window.plus(cooldown).toSeconds());
        jdbc.update("WITH expired AS (SELECT ctid FROM ontology.og_credential_request_bucket "
                        + "WHERE updated_at < NOW() - (? * INTERVAL '1 second') "
                        + "ORDER BY updated_at LIMIT ? FOR UPDATE SKIP LOCKED) "
                        + "DELETE FROM ontology.og_credential_request_bucket b USING expired e WHERE b.ctid = e.ctid",
                ageSeconds, CLEANUP_BATCH_SIZE);
    }

    private byte[] digest(final String type, final String dimension, final String value) {
        try {
            String input = type + "\u0000" + dimension + "\u0000" + (value == null ? "unknown" : value.trim());
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 未提供 SHA-256", impossible);
        }
    }
}
