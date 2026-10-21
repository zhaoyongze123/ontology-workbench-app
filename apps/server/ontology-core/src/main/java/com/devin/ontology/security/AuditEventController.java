package com.devin.ontology.security;

import com.devin.ontology.identity.InternalUser;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统管理员只读审计入口；查询、参数校验和 SQL 由查询服务负责。 */
@RestController
@RequestMapping("/admin/audit-events")
@Profile("multiuser")
public class AuditEventController {
    private final CurrentIdentity currentIdentity;
    private final AuditEventQueryService queryService;

    public AuditEventController(final CurrentIdentity currentIdentity,
            final AuditEventQueryService queryService) {
        this.currentIdentity = currentIdentity;
        this.queryService = queryService;
    }

    @GetMapping
    public List<AuditEventView> list(@RequestParam(defaultValue = "100") final int limit,
            @RequestParam(required = false) final Long beforeId,
            @RequestParam(required = false) final OffsetDateTime startTime,
            @RequestParam(required = false) final OffsetDateTime endTime,
            @RequestParam(required = false) final List<String> eventTypes,
            @RequestParam(required = false) final String actorUserId) {
        InternalUser actor = currentIdentity.requireUser();
        if (!actor.systemAdmin()) throw new AccessDeniedException("仅系统管理员可查看安全审计");
        return queryService.list(new AuditEventQueryService.Filter(
                limit, beforeId, startTime, endTime, eventTypes, actorUserId));
    }

    /** 保持既有数组响应字段不变，筛选仅扩展请求参数。 */
    public record AuditEventView(long id, String eventType, String actorUserId,
            String actorDisplayName, String actorUsername, String objectDisplayName,
            com.fasterxml.jackson.databind.JsonNode details, java.time.Instant createdAt) {
    }
}
