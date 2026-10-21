package com.devin.ontology.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 失败与拒绝事件在独立事务中追加，避免业务回滚抹掉安全证据。 */
@Service
public class SecurityAuditEventWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SecurityAuditEventWriter(final JdbcTemplate jdbc, final ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendFailure(final String eventType, final String actorUserId,
            final Map<String, Object> metadata) {
        try {
            String details = mapper.writeValueAsString(metadata);
            jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                    + "VALUES (?, ?, ?::jsonb)", eventType, actorUserId, details);
        } catch (JsonProcessingException invalidMetadata) {
            // 结构化元数据来自受控字段；序列化失败不应退化为拼接 JSON。
            throw new IllegalStateException("安全审计元数据序列化失败", invalidMetadata);
        }
    }
}
