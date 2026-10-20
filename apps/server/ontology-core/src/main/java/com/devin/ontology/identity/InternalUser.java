package com.devin.ontology.identity;

import java.io.Serializable;

/** 身份认证和授权层使用的内部用户视图。 */
public record InternalUser(
        String id,
        String username,
        String displayName,
        boolean systemAdmin,
        boolean enabled,
        long authVersion) implements Serializable {
}
