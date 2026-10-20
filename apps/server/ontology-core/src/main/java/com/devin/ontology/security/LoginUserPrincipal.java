package com.devin.ontology.security;

import java.io.Serializable;

/** 会话内的最小身份快照，不包含密码或密码散列。 */
public record LoginUserPrincipal(
        String userId,
        String username,
        String displayName,
        boolean systemAdmin,
        long authVersion,
        long loginAtEpochMillis) implements Serializable, java.security.Principal {
    /** 框架持久会话及 OAuth 授权只使用稳定内部 ID，不使用 record 的展示字符串。 */
    @Override
    public String getName() {
        return userId;
    }
}
