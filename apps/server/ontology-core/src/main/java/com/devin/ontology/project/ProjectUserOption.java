package com.devin.ontology.project;

/** 邀请选择器使用可读名称展示；内部 ID 仅用于提交，待处理邀请只读显示。 */
public record ProjectUserOption(String id, String username, String displayName, boolean invitationPending) {
}
