package com.devin.ontology.oidc;

/** 由 Spring Security 验证过 ID Token 后提取的上游身份声明。 */
public record VerifiedWorkbenchIdentity(String issuer, String subject, String displayName, String email) {
    public VerifiedWorkbenchIdentity(final String issuer, final String subject, final String displayName) {
        this(issuer, subject, displayName, null);
    }
}
