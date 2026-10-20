package com.devin.ontology.security;

import com.devin.ontology.identity.InternalUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统管理员只读查看审计；应用 API 不提供修改或删除审计记录的入口。 */
@RestController
@RequestMapping("/admin/audit-events")
@Profile("multiuser")
public class AuditEventController {
    private static final int MAX_PAGE_SIZE = 200;

    private final CurrentIdentity currentIdentity;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditEventController(final CurrentIdentity currentIdentity, final JdbcTemplate jdbc,
            final ObjectMapper mapper) {
        this.currentIdentity = currentIdentity;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditEventView> list(@RequestParam(defaultValue = "100") final int limit,
            @RequestParam(required = false) final Long beforeId) {
        InternalUser actor = currentIdentity.requireUser();
        if (!actor.systemAdmin()) throw new AccessDeniedException("仅系统管理员可查看安全审计");
        if (limit < 1 || limit > MAX_PAGE_SIZE || (beforeId != null && beforeId < 1)) {
            throw new IllegalArgumentException("审计分页参数超出允许范围");
        }

        String base = "SELECT id, event_type, actor_user_id, details_json, create_time "
                + "FROM ontology.og_audit_event ";
        if (beforeId == null) {
            return jdbc.query(base + "ORDER BY id DESC LIMIT ?", this::mapEvent, limit);
        }
        return jdbc.query(base + "WHERE id < ? ORDER BY id DESC LIMIT ?", this::mapEvent, beforeId, limit);
    }

    private AuditEventView mapEvent(final java.sql.ResultSet rs, final int rowNum) throws java.sql.SQLException {
        JsonNode details;
        try {
            details = mapper.readTree(rs.getString("details_json"));
        } catch (java.io.IOException invalidStoredJson) {
            // jsonb 应保证有效；若历史数据损坏，不把底层异常内容回传给管理端。
            details = mapper.createObjectNode().put("unavailable", true);
        }
        Timestamp timestamp = rs.getTimestamp("create_time");
        return new AuditEventView(rs.getLong("id"), rs.getString("event_type"),
                rs.getString("actor_user_id"), details, timestamp == null ? null : timestamp.toInstant());
    }

    public record AuditEventView(long id, String eventType, String actorUserId, JsonNode details, Instant createdAt) {
    }
}
