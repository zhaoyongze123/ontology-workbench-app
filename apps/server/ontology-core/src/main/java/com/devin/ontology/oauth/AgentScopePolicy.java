package com.devin.ontology.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.core.Authentication;

/** OAuth scope 只表达客户端工具权限；资源是否属于当前用户仍由业务层项目 ACL 判断。 */
public final class AgentScopePolicy {
    private static final Set<String> READ_ACTIONS = Set.of(
            "/cypher/query", "/graph/page", "/graph/getInfo", "/graph/link/getOntologyNodeList",
            "/graph/export/json", "/node/page", "/node/getNodeInfo", "/node/optional",
            "/relationship/page", "/relationship/info", "/relationship/optional",
            "/relationship/link/getOntologyRelationshipList", "/organization/page");

    public boolean isAllowed(final Authentication authentication, final HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("/oidc/app/userinfo".equals(path)) return has(authentication, "profile");
        if (path.startsWith("/projects/") || path.startsWith("/project-invitations/")
                || path.startsWith("/users/")) return has(authentication, "ontology.manage");
        if (READ_ACTIONS.contains(path) || path.endsWith("/page") || path.endsWith("/info")
                || path.endsWith("/getInfo") || path.endsWith("/optional") || path.endsWith("/export/json")) {
            return has(authentication, "ontology.read");
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())) {
            return has(authentication, "ontology.read");
        }
        return has(authentication, "ontology.write");
    }

    private boolean has(final Authentication authentication, final String scope) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                ("SCOPE_" + scope).equals(authority.getAuthority()));
    }
}
