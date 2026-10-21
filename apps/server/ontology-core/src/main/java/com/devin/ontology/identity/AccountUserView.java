package com.devin.ontology.identity;

import java.time.LocalDateTime;

/** 管理台可见的账号字段，不包含凭证散列或一次性链接摘要。 */
public record AccountUserView(
        String id,
        String username,
        String displayName,
        boolean systemAdmin,
        boolean enabled,
        boolean passwordActivated,
        LocalDateTime createTime) {
}
