package com.devin.ontology.project;

import java.time.Instant;
import java.util.UUID;

/** 邀请公开视图，不返回邮箱、密码或安全角色。 */
public record ProjectInvitationView(
        UUID id,
        String projectName,
        String inviterUserId,
        String inviterDisplayName,
        String targetUserId,
        String targetDisplayName,
        ProjectRole role,
        String status,
        Instant createdTime,
        Instant expiresTime) {
}
