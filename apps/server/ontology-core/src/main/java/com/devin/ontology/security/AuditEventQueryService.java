package com.devin.ontology.security;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审计筛选规则与参数化查询集中在服务层，控制器只承担身份与权限边界。 */
@Service
public class AuditEventQueryService {
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_EVENT_TYPES = 50;
    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,119}");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditEventQueryService(final NamedParameterJdbcTemplate jdbc, final ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AuditEventController.AuditEventView> list(final Filter filter) {
        validate(filter);
        StringBuilder sql = new StringBuilder("SELECT e.id, e.event_type, e.actor_user_id, e.details_json, "
                + "actor.display_name AS actor_display_name, actor.username AS actor_username, "
                + "CASE WHEN target.id IS NOT NULL THEN COALESCE(target.display_name, target.username) "
                + "WHEN project.name IS NOT NULL THEN project.name "
                + "WHEN graph.name IS NOT NULL THEN graph.name "
                + "WHEN app.display_name IS NOT NULL THEN app.display_name "
                + "WHEN jsonb_exists(e.details_json, 'invitationId') THEN '项目邀请' ELSE NULL END AS object_display_name, "
                + "((EXTRACT(EPOCH FROM (e.create_time AT TIME ZONE current_setting('TimeZone'))) * 1000)::bigint) "
                + "AS created_at_epoch_ms FROM ontology.og_audit_event e "
                + "LEFT JOIN ontology.og_user actor ON actor.id = e.actor_user_id "
                + "LEFT JOIN ontology.og_user target ON target.id::text = e.details_json->>'targetUserId' "
                + "LEFT JOIN ontology.og_organization project ON project.id::text = e.details_json->>'projectId' "
                + "LEFT JOIN ontology.og_graph graph ON graph.id::text = e.details_json->>'graphId' "
                + "LEFT JOIN ontology.og_app_client app ON app.client_id = e.details_json->>'clientId' "
                + "WHERE 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("limit", filter.limit());

        if (filter.beforeId() != null) {
            sql.append(" AND e.id < :beforeId");
            parameters.addValue("beforeId", filter.beforeId());
        }
        if (filter.startTime() != null) {
            // TIMESTAMP without zone stores wall time in the database session zone; convert bounds to that same zone.
            sql.append(" AND e.create_time >= (CAST(:startTime AS TIMESTAMPTZ) "
                    + "AT TIME ZONE current_setting('TimeZone'))");
            parameters.addValue("startTime", filter.startTime());
        }
        if (filter.endTime() != null) {
            sql.append(" AND e.create_time <= (CAST(:endTime AS TIMESTAMPTZ) "
                    + "AT TIME ZONE current_setting('TimeZone'))");
            parameters.addValue("endTime", filter.endTime());
        }
        if (filter.eventTypes() != null && !filter.eventTypes().isEmpty()) {
            sql.append(" AND e.event_type IN (:eventTypes)");
            parameters.addValue("eventTypes", filter.eventTypes());
        }
        if (filter.actorUserId() != null && !filter.actorUserId().isBlank()) {
            sql.append(" AND e.actor_user_id = :actorUserId");
            parameters.addValue("actorUserId", filter.actorUserId().trim());
        }

        sql.append(" ORDER BY e.id DESC LIMIT :limit");
        return jdbc.query(sql.toString(), parameters, (rs, rowNum) -> {
            JsonNode details;
            try {
                details = mapper.readTree(rs.getString("details_json"));
            } catch (IOException invalidStoredJson) {
                // 历史记录异常仅转为安全占位，不把数据库错误内容回传给调用者。
                details = mapper.createObjectNode().put("unavailable", true);
            }
            long epochMillis = rs.getLong("created_at_epoch_ms");
            Instant createdAt = rs.wasNull() ? null : Instant.ofEpochMilli(epochMillis);
            return new AuditEventController.AuditEventView(rs.getLong("id"), rs.getString("event_type"),
                    rs.getString("actor_user_id"), rs.getString("actor_display_name"),
                    rs.getString("actor_username"), rs.getString("object_display_name"), details, createdAt);
        });
    }

    private static void validate(final Filter filter) {
        if (filter.limit() < 1 || filter.limit() > MAX_PAGE_SIZE
                || (filter.beforeId() != null && filter.beforeId() < 1)) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        if (filter.startTime() != null && filter.endTime() != null
                && filter.startTime().isAfter(filter.endTime())) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        if (filter.eventTypes() != null) {
            if (filter.eventTypes().size() > MAX_EVENT_TYPES
                    || filter.eventTypes().stream().anyMatch(type -> type == null || !EVENT_TYPE.matcher(type).matches())) {
                throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
            }
        }
        if (filter.actorUserId() != null && !filter.actorUserId().isBlank()) {
            String value = filter.actorUserId().trim();
            try {
                if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                    throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
                }
            } catch (IllegalArgumentException invalidUuid) {
                throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
            }
        }
    }

    public record Filter(int limit, Long beforeId, OffsetDateTime startTime, OffsetDateTime endTime,
            List<String> eventTypes, String actorUserId) {
        public Filter {
            eventTypes = eventTypes == null ? null : List.copyOf(eventTypes);
        }
    }
}
