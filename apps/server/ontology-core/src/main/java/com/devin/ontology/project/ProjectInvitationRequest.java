package com.devin.ontology.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 创建邀请的请求只接收目标内部用户 id 和可授予项目角色。 */
public record ProjectInvitationRequest(
        @NotBlank String targetUserId,
        @NotNull ProjectRole role) {
}
