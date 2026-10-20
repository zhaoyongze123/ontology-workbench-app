package com.devin.ontology.project;

/** 项目成员公开视图，不包含凭据或用户隐私字段。 */
public record ProjectMemberView(String userId, String username, String displayName, ProjectRole role) {
}
