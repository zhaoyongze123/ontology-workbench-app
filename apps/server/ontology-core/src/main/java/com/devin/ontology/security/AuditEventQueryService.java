package com.devin.ontology.security;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
    private static final ZoneId DATABASE_LOCAL_ZONE = ZoneId.of("Asia/Shanghai");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditEventQueryService(final NamedParameterJdbcTemplate jdbc, final ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AuditEventController.AuditEventView> list(final Filter filter) {
        validate(filter);
        StringBuilder sql = new StringBuilder("SELECT id, event_type, actor_user_id, details_json, create_time "
                + "FROM ontology.og_audit_event WHERE 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("limit", filter.limit());

        if (filter.beforeId() != null) {
            sql.append(" AND id < :beforeId");
            parameters.addValue("beforeId", filter.beforeId());
        }
        if (filter.startTime() != null) {
            sql.append(" AND create_time >= :startTime");
            parameters.addValue("startTime", databaseLocalTime(filter.startTime()));
        }
        if (filter.endTime() != null) {
            sql.append(" AND create_time <= :endTime");
            parameters.addValue("endTime", databaseLocalTime(filter.endTime()));
        }
        if (filter.eventTypes() != null && !filter.eventTypes().isEmpty()) {
            sql.append(" AND event_type IN (:eventTypes)");
            parameters.addValue("eventTypes", filter.eventTypes());
        }
        if (filter.actorUserId() != null && !filter.actorUserId().isBlank()) {
            sql.append(" AND actor_user_id = :actorUserId");
            parameters.addValue("actorUserId", filter.actorUserId().trim());
        }

        sql.append(" ORDER BY id DESC LIMIT :limit");
        return jdbc.query(sql.toString(), parameters, (rs, rowNum) -> {
            JsonNode details;
            try {
                details = mapper.readTree(rs.getString("details_json"));
            } catch (IOException invalidStoredJson) {
                // 历史记录异常仅转为安全占位，不把数据库错误内容回传给调用者。
                details = mapper.createObjectNode().put("unavailable", true);
            }
            Timestamp timestamp = rs.getTimestamp("create_time");
            return new AuditEventController.AuditEventView(rs.getLong("id"), rs.getString("event_type"),
                    rs.getString("actor_user_id"), details,
                    timestamp == null ? null : timestamp.toInstant());
        });
    }

    private static java.time.LocalDateTime databaseLocalTime(final OffsetDateTime value) {
        // 历史表使用无时区 TIMESTAMP；将带时区输入归一到服务明确的北京时间后比较。
        return value.atZoneSameInstant(DATABASE_LOCAL_ZONE).toLocalDateTime();
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
