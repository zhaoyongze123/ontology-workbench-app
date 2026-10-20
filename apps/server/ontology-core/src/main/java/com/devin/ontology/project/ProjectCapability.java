package com.devin.ontology.project;

/** 项目资源支持的授权能力。 */
public enum ProjectCapability {
    READ,
    EDIT,
    DELETE_GRAPH,
    MANAGE_MEMBERS,
    DELETE_PROJECT,
    TRANSFER_OWNERSHIP,
    /** 项目基本信息维护能力，暂供现有编辑入口使用。 */
    EDIT_PROJECT
}
