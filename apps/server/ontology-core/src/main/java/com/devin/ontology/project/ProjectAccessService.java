package com.devin.ontology.project;

import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.CurrentIdentity;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** 项目及图谱授权的唯一入口；每次请求均读取当前成员事实。 */
@Service
public class ProjectAccessService {
    private static final Map<ProjectRole, Set<ProjectCapability>> ROLE_CAPABILITIES = Map.of(
            ProjectRole.OWNER, EnumSet.allOf(ProjectCapability.class),
            ProjectRole.ADMIN, EnumSet.of(ProjectCapability.READ, ProjectCapability.EDIT,
                    ProjectCapability.DELETE_GRAPH, ProjectCapability.MANAGE_MEMBERS,
                    ProjectCapability.EDIT_PROJECT),
            ProjectRole.EDITOR, EnumSet.of(ProjectCapability.READ, ProjectCapability.EDIT),
            ProjectRole.VIEWER, EnumSet.of(ProjectCapability.READ));

    private final JdbcTemplate jdbcTemplate;
    private final CurrentIdentity currentIdentity;

    public ProjectAccessService(final JdbcTemplate jdbcTemplate, final CurrentIdentity currentIdentity) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentIdentity = currentIdentity;
    }

    public InternalUser currentUser() {
        InternalUser user = currentIdentity.requireUser();
        if (!user.enabled()) {
            throw denied();
        }
        return user;
    }

    public void requireProject(final BigInteger projectId, final ProjectCapability capability) {
        InternalUser user = currentUser();
        if (projectId == null || capability == null) {
            throw denied();
        }
        if (user.systemAdmin()) {
            // 全局管理员对已存在资源有全部能力；仍检查资源是否存在。
            if (!projectExists(projectId)) {
                throw denied();
            }
            return;
        }
        ProjectRole memberRole = memberRole(user, projectId);
        if (!ROLE_CAPABILITIES.get(memberRole).contains(capability)) {
            throw denied();
        }
    }

    /** 读取当前用户在项目中的实际权限；系统管理员仍要求项目真实存在。 */
    public ProjectAccessView describeProject(final BigInteger projectId) {
        InternalUser user = currentUser();
        if (projectId == null) throw denied();
        if (user.systemAdmin()) {
            if (!projectExists(projectId)) throw denied();
            return new ProjectAccessView("SYSTEM_ADMIN", capabilityNames(ROLE_CAPABILITIES.get(ProjectRole.OWNER)));
        }
        ProjectRole role = memberRole(user, projectId);
        Set<ProjectCapability> capabilities = ROLE_CAPABILITIES.get(role);
        if (!capabilities.contains(ProjectCapability.READ)) throw denied();
        return new ProjectAccessView(role.name(), capabilityNames(capabilities));
    }

    public void requireGraph(final BigInteger graphId, final ProjectCapability capability) {
        if (graphId == null) {
            throw denied();
        }
        BigInteger projectId = jdbcTemplate.query(
                "SELECT organization_id FROM ontology.og_graph WHERE id = ?",
                (rs, rowNum) -> rs.getBigDecimal(1).toBigInteger(), graphId)
                .stream().findFirst().orElseThrow(ProjectAccessService::denied);
        requireProject(projectId, capability);
    }

    public boolean isSystemAdmin() {
        return currentUser().systemAdmin();
    }

    private ProjectRole memberRole(final InternalUser user, final BigInteger projectId) {
        String role = jdbcTemplate.query(
                "SELECT m.role "
                        + "FROM ontology.og_project_member m "
                        + "JOIN ontology.og_organization p ON p.id = m.project_id "
                        + "WHERE m.user_id = ? AND p.id = ?",
                (rs, rowNum) -> java.util.Objects.toString(rs.getString("role"), ""), user.id(), projectId)
                .stream().findFirst().orElseThrow(ProjectAccessService::denied);
        try {
            return ProjectRole.valueOf(role);
        } catch (RuntimeException e) {
            throw denied();
        }
    }

    private Set<String> capabilityNames(final Set<ProjectCapability> capabilities) {
        return capabilities.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private boolean projectExists(final BigInteger id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ontology.og_organization WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Project access denied");
    }
}
