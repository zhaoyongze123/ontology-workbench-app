package com.devin.ontology.identity;

import java.io.Serializable;

/** 身份认证和授权层使用的内部用户视图。 */
public record InternalUser(
        String id,
        String username,
        String displayName,
        boolean systemAdmin,
        boolean enabled,
        boolean passwordActivated,
        long authVersion) implements Serializable {

    /** 保持旧测试与不涉及本地密码激活的调用语义；历史用户默认视为已激活。 */
    public InternalUser(final String id, final String username, final String displayName,
            final boolean systemAdmin, final boolean enabled, final long authVersion) {
        this(id, username, displayName, systemAdmin, enabled, true, authVersion);
    }
}
