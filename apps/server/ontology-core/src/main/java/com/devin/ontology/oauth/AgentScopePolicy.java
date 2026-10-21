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
    private static final Set<String> WRITE_ACTIONS = Set.of(
            "/graph/save", "/graph/edit", "/graph/delete", "/graph/import/json",
            "/node/saveNode", "/node/editNode", "/node/deleteNode",
            "/relationship/save", "/relationship/edit", "/relationship/delete");
    private static final Set<String> PROJECT_MANAGEMENT_ACTIONS = Set.of(
            "/organization/save", "/organization/edit", "/organization/delete");

    public boolean isAllowed(final Authentication authentication, final HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("/oidc/app/userinfo".equals(path)) return has(authentication, "profile");
        if (path.startsWith("/projects/") || path.startsWith("/project-invitations/")
                || path.startsWith("/users/") || PROJECT_MANAGEMENT_ACTIONS.contains(path)) {
            return has(authentication, "ontology.manage");
        }
        if (READ_ACTIONS.contains(path)) {
            return has(authentication, "ontology.read");
        }
        if (WRITE_ACTIONS.contains(path)) return has(authentication, "ontology.write");
        // 新增 API 必须先明确归类，避免未知 GET 或写路由意外继承宽泛 scope。
        return false;
    }

    private boolean has(final Authentication authentication, final String scope) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                ("SCOPE_" + scope).equals(authority.getAuthority()));
    }
}
