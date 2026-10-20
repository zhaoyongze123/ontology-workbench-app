package com.devin.ontology.project;

import java.util.Set;

/** 当前用户对项目的权限视图，不承载项目成员或凭据数据。 */
public record ProjectAccessView(String role, Set<String> capabilities) {
    public ProjectAccessView {
        capabilities = Set.copyOf(capabilities);
    }
}
