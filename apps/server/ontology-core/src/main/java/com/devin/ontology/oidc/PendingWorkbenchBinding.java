package com.devin.ontology.oidc;

import java.io.Serializable;

/** 存入服务端 session 的短时绑定意图，不含上游令牌或授权码。 */
public record PendingWorkbenchBinding(String userId, long authVersion,
        long loginAtEpochMillis, long expiresAtEpochMillis) implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String SESSION_ATTRIBUTE = PendingWorkbenchBinding.class.getName() + ".pending";
}
