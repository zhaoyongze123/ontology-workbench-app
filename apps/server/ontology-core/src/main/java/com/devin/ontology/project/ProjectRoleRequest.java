package com.devin.ontology.project;

import jakarta.validation.constraints.NotNull;

/** 成员角色修改请求。OWNER 角色会在服务层拒绝。 */
public record ProjectRoleRequest(@NotNull ProjectRole role) {
}
