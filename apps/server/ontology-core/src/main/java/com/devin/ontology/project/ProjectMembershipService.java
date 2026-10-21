package com.devin.ontology.project;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.identity.InternalUser;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目成员与站内邀请的业务边界；所有写入都在项目行锁和审计事务内完成。 */
@Service
public class ProjectMembershipService {
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final ProjectAccessService access;

    public ProjectMembershipService(final JdbcTemplate jdbc, final ProjectAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public ProjectAccessView access(final BigInteger projectId) {
        return access.describeProject(projectId);
    }

    @Transactional(readOnly = true)
    public ProjectPage<ProjectMemberView> members(final BigInteger projectId, final int pageNum, final int pageSize) {
        validatePage(pageNum, pageSize);
        access.requireProject(projectId, ProjectCapability.MANAGE_MEMBERS);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_project_member WHERE project_id = ?",
                Long.class, projectId);
        List<ProjectMemberView> rows = jdbc.query(
                "SELECT u.id, u.username, u.display_name, m.role FROM ontology.og_project_member m "
                        + "JOIN ontology.og_user u ON u.id = m.user_id WHERE m.project_id = ? "
                        + "ORDER BY CASE m.role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END, u.username, u.id "
                        + "LIMIT ? OFFSET ?",
                (rs, n) -> new ProjectMemberView(rs.getString("id"), rs.getString("username"),
                        rs.getString("display_name"), ProjectRole.valueOf(rs.getString("role"))),
                projectId, pageSize, offset(pageNum, pageSize));
        return new ProjectPage<>(rows, pageNum, pageSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ProjectPage<ProjectInvitationView> mine(final int pageNum, final int pageSize) {
        validatePage(pageNum, pageSize);
        String userId = access.currentUser().id();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_project_invitation "
                + "WHERE target_user_id = ? AND status = 'PENDING' AND expires_time > NOW()", Long.class, userId);
        List<ProjectInvitationView> rows = jdbc.query(invitationSelect()
                        + " WHERE i.target_user_id = ? AND i.status = 'PENDING' AND i.expires_time > NOW() "
                        + "ORDER BY i.created_time DESC, i.id DESC LIMIT ? OFFSET ?",
                ProjectMembershipService::mapInvitation, userId, pageSize, offset(pageNum, pageSize));
        return new ProjectPage<>(rows, pageNum, pageSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ProjectPage<ProjectUserOption> searchUsers(final String query, final int pageNum, final int pageSize) {
        validatePage(pageNum, pageSize);
        access.currentUser();
        if (query == null || query.trim().length() < 2) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        String escaped = escapeLike(query.trim());
        String pattern = "%" + escaped + "%";
        String where = " FROM ontology.og_user u WHERE u.enabled = TRUE AND "
                + "(u.username ILIKE ? ESCAPE E'\\\\' OR u.display_name ILIKE ? ESCAPE E'\\\\')";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, pattern, pattern);
        List<ProjectUserOption> rows = jdbc.query("SELECT u.id, u.username, u.display_name" + where
                        + " ORDER BY u.username, u.id LIMIT ? OFFSET ?",
                (rs, n) -> new ProjectUserOption(rs.getString("id"), rs.getString("username"),
                        rs.getString("display_name")), pattern, pattern, pageSize, offset(pageNum, pageSize));
        return new ProjectPage<>(rows, pageNum, pageSize, total == null ? 0 : total);
    }

    @Transactional
    public ProjectInvitationView invite(final BigInteger projectId, final ProjectInvitationRequest request) {
        if (request == null || request.targetUserId() == null || request.role() == null) {
            throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
        }
        InternalUser actor = access.currentUser();
        lockProject(projectId);
        access.requireProject(projectId, ProjectCapability.MANAGE_MEMBERS);
        ProjectRole actorRole = actorRole(projectId, actor.id());
        ProjectRole requested = request.role();
        validateGrantRole(requested);
        if (!actor.systemAdmin() && actorRole == ProjectRole.ADMIN && requested != ProjectRole.EDITOR
                && requested != ProjectRole.VIEWER) {
            throw denied();
        }
        if (actorRole != ProjectRole.OWNER && actorRole != ProjectRole.ADMIN && !actor.systemAdmin()) {
            throw denied();
        }
        String target = request.targetUserId().trim();
        requireEnabledUser(target);
        if (memberExists(projectId, target)) {
            throw new OntologyApiException(ApiErrorCode.PROJECT_ALREADY_MEMBER);
        }
        List<UUID> expiredInvitations = jdbc.query("SELECT id FROM ontology.og_project_invitation "
                        + "WHERE project_id = ? AND target_user_id = ? AND status = 'PENDING' AND expires_time <= NOW() FOR UPDATE",
                (rs, n) -> rs.getObject(1, UUID.class), projectId, target);
        for (UUID expiredId : expiredInvitations) {
            setInvitationStatus(expiredId, "EXPIRED");
            audit("PROJECT_INVITATION_EXPIRED", actor.id(), projectId, "MANAGE_MEMBERS", "invitationId",
                    expiredId.toString());
        }
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_project_invitation "
                + "WHERE project_id = ? AND target_user_id = ? AND status = 'PENDING'", Integer.class, projectId, target);
        if (pending != null && pending > 0) {
            throw new OntologyApiException(ApiErrorCode.INVITATION_PENDING);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ontology.og_project_invitation "
                        + "(id, project_id, inviter_user_id, inviter_role, inviter_system_admin, target_user_id, role, expires_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW() + INTERVAL '7 days')",
                id, projectId, actor.id(), actorRole == null ? null : actorRole.name(), actor.systemAdmin(), target,
                requested.name());
        audit("PROJECT_INVITATION_CREATED", actor.id(), projectId, "MANAGE_MEMBERS", "invitationId", id.toString());
        return invitationById(id);
    }

    @Transactional
    public ProjectInvitationView accept(final UUID invitationId) {
        InternalUser actor = access.currentUser();
        BigInteger projectId = invitationProject(invitationId);
        lockProject(projectId);
        Invitation invitation = lockInvitation(invitationId);
        if (!invitation.targetId().equals(actor.id())) {
            throw denied();
        }
        if ("ACCEPTED".equals(invitation.status())) {
            return invitationById(invitationId);
        }
        if (!"PENDING".equals(invitation.status())) {
            return invitationById(invitationId);
        }
        if (!invitation.expiresTime().isAfter(Instant.now())) {
            setInvitationStatus(invitationId, "EXPIRED");
            audit("PROJECT_INVITATION_EXPIRED", actor.id(), projectId, "ACCEPT_INVITATION", "invitationId",
                    invitationId.toString());
            return invitationById(invitationId);
        }
        if (!inviterStillAuthorized(projectId, invitation)) {
            setInvitationStatus(invitationId, "REVOKED");
            audit("PROJECT_INVITATION_REVOKED", actor.id(), projectId, "ACCEPT_INVITATION", "invitationId",
                    invitationId.toString());
            return invitationById(invitationId);
        }
        requireEnabledUser(actor.id());
        if (memberExists(projectId, actor.id())) {
            setInvitationStatus(invitationId, "REVOKED");
            audit("PROJECT_INVITATION_REVOKED", actor.id(), projectId, "ACCEPT_INVITATION", "invitationId",
                    invitationId.toString());
            return invitationById(invitationId);
        }
        jdbc.update("INSERT INTO ontology.og_project_member(project_id, user_id, role) VALUES (?, ?, ?)",
                projectId, actor.id(), invitation.role().name());
        setInvitationStatus(invitationId, "ACCEPTED");
        audit("PROJECT_INVITATION_ACCEPTED", actor.id(), projectId, "ACCEPT_INVITATION", "invitationId",
                invitationId.toString());
        return invitationById(invitationId);
    }

    @Transactional
    public ProjectInvitationView decline(final UUID invitationId) {
        InternalUser actor = access.currentUser();
        BigInteger projectId = invitationProject(invitationId);
        lockProject(projectId);
        Invitation invitation = lockInvitation(invitationId);
        if (!invitation.targetId().equals(actor.id())) {
            throw denied();
        }
        if (!"PENDING".equals(invitation.status())) {
            return invitationById(invitationId);
        }
        String status = invitation.expiresTime().isAfter(Instant.now()) ? "DECLINED" : "EXPIRED";
        setInvitationStatus(invitationId, status);
        audit("PROJECT_INVITATION_" + status, actor.id(), projectId, "DECLINE_INVITATION", "invitationId",
                invitationId.toString());
        return invitationById(invitationId);
    }

    @Transactional
    public void changeRole(final BigInteger projectId, final String targetId, final ProjectRole role) {
        InternalUser actor = access.currentUser();
        lockProject(projectId);
        access.requireProject(projectId, ProjectCapability.MANAGE_MEMBERS);
        ProjectRole actorRole = actorRole(projectId, actor.id());
        ProjectRole oldRole = lockMemberRole(projectId, targetId);
        validateGrantRole(role);
        if (oldRole == ProjectRole.OWNER || targetId.equals(actor.id())) {
            throw denied();
        }
        if (!canManage(actor, actorRole, oldRole, role)) {
            throw denied();
        }
        if (oldRole == role) return;
        jdbc.update("UPDATE ontology.og_project_member SET role = ? WHERE project_id = ? AND user_id = ?",
                role.name(), projectId, targetId);
        audit("PROJECT_MEMBER_ROLE_CHANGED", actor.id(), projectId, "MANAGE_MEMBERS", "targetUserId", targetId);
    }

    @Transactional
    public void removeMember(final BigInteger projectId, final String targetId) {
        InternalUser actor = access.currentUser();
        lockProject(projectId);
        access.requireProject(projectId, ProjectCapability.MANAGE_MEMBERS);
        ProjectRole actorRole = actorRole(projectId, actor.id());
        ProjectRole oldRole = lockMemberRole(projectId, targetId);
        if (oldRole == ProjectRole.OWNER || targetId.equals(actor.id()) || !canManage(actor, actorRole, oldRole, null)) {
            throw denied();
        }
        jdbc.update("DELETE FROM ontology.og_project_member WHERE project_id = ? AND user_id = ?", projectId, targetId);
        audit("PROJECT_MEMBER_REMOVED", actor.id(), projectId, "MANAGE_MEMBERS", "targetUserId", targetId);
    }

    @Transactional
    public void leave(final BigInteger projectId) {
        InternalUser actor = access.currentUser();
        lockProject(projectId);
        ProjectRole role = memberRoleOrNull(projectId, actor.id());
        if (role == null || role == ProjectRole.OWNER) throw denied();
        jdbc.update("DELETE FROM ontology.og_project_member WHERE project_id = ? AND user_id = ?", projectId, actor.id());
        audit("PROJECT_MEMBER_LEFT", actor.id(), projectId, "LEAVE_PROJECT", "targetUserId", actor.id());
    }

    @Transactional
    public void transferOwner(final BigInteger projectId, final String targetId) {
        InternalUser actor = access.currentUser();
        lockProject(projectId);
        access.requireProject(projectId, ProjectCapability.MANAGE_MEMBERS);
        ProjectRole actorRole = memberRoleOrNull(projectId, actor.id());
        if (!actor.systemAdmin() && actorRole != ProjectRole.OWNER) throw denied();
        requireEnabledUser(targetId);
        ProjectRole targetRole = memberRoleOrNull(projectId, targetId);
        if (targetRole == null) throw new OntologyApiException(ApiErrorCode.PROJECT_OWNER_CONFLICT);
        if (targetRole == ProjectRole.OWNER) return;
        String oldOwner = jdbc.query("SELECT user_id FROM ontology.og_project_member WHERE project_id = ? AND role = 'OWNER' FOR UPDATE",
                (rs, n) -> rs.getString(1), projectId).stream().findFirst().orElseThrow(ProjectMembershipService::denied);
        jdbc.query("SELECT user_id FROM ontology.og_project_member WHERE project_id = ? AND user_id IN (?, ?) "
                        + "ORDER BY user_id FOR UPDATE", (rs, n) -> rs.getString(1), projectId, oldOwner, targetId);
        jdbc.update("UPDATE ontology.og_project_member SET role = 'ADMIN' WHERE project_id = ? AND user_id = ? AND role = 'OWNER'",
                projectId, oldOwner);
        jdbc.update("UPDATE ontology.og_project_member SET role = 'OWNER' WHERE project_id = ? AND user_id = ?",
                projectId, targetId);
        audit("PROJECT_OWNERSHIP_TRANSFERRED", actor.id(), projectId, "TRANSFER_OWNERSHIP", "targetUserId", targetId);
    }

    private boolean inviterStillAuthorized(final BigInteger projectId, final Invitation i) {
        if (i.inviterId() == null) return false;
        List<InviterState> state = jdbc.query("SELECT enabled, system_admin FROM ontology.og_user WHERE id = ?",
                (rs, n) -> new InviterState(rs.getBoolean("enabled"), rs.getBoolean("system_admin")), i.inviterId());
        // 发起邀请时为系统管理员的用户若后来失去该身份，邀请也随之失效。
        if (state.isEmpty() || !state.get(0).enabled() || state.get(0).systemAdmin() != i.inviterSystemAdmin()) return false;
        ProjectRole now = memberRoleOrNull(projectId, i.inviterId());
        if (i.inviterSystemAdmin()) {
            return now == i.inviterRole() && i.inviterSystemAdmin();
        }
        if (now == null || now != i.inviterRole()) return false;
        return now == ProjectRole.OWNER || now == ProjectRole.ADMIN
                && (i.role() == ProjectRole.EDITOR || i.role() == ProjectRole.VIEWER);
    }

    static boolean canManage(final InternalUser actor, final ProjectRole actorRole,
                             final ProjectRole oldRole, final ProjectRole newRole) {
        if (actor.systemAdmin()) return oldRole != ProjectRole.OWNER && (newRole == null || newRole != ProjectRole.OWNER);
        if (actorRole == ProjectRole.OWNER) return oldRole != ProjectRole.OWNER && (newRole == null || newRole != ProjectRole.OWNER);
        return actorRole == ProjectRole.ADMIN && oldRole != ProjectRole.OWNER && oldRole != ProjectRole.ADMIN
                && (newRole == null || newRole == ProjectRole.EDITOR || newRole == ProjectRole.VIEWER);
    }

    private void validateGrantRole(final ProjectRole role) {
        if (role == null || role == ProjectRole.OWNER) throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
    }

    private void requireEnabledUser(final String userId) {
        Boolean enabled = jdbc.query("SELECT enabled FROM ontology.og_user WHERE id = ? FOR UPDATE",
                (rs, n) -> rs.getBoolean(1), userId).stream().findFirst().orElse(false);
        if (!enabled) throw new OntologyApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    private void lockProject(final BigInteger id) {
        jdbc.query("SELECT id FROM ontology.og_organization WHERE id = ? FOR UPDATE", (rs, n) -> rs.getLong(1), id)
                .stream().findFirst().orElseThrow(ProjectMembershipService::denied);
    }

    private ProjectRole actorRole(final BigInteger projectId, final String userId) {
        ProjectRole role = memberRoleOrNull(projectId, userId);
        if (role == null && !access.isSystemAdmin()) throw denied();
        return role;
    }

    private ProjectRole lockMemberRole(final BigInteger projectId, final String userId) {
        return jdbc.query("SELECT role FROM ontology.og_project_member WHERE project_id = ? AND user_id = ? FOR UPDATE",
                        (rs, n) -> ProjectRole.valueOf(rs.getString(1)), projectId, userId)
                .stream().findFirst().orElseThrow(ProjectMembershipService::denied);
    }

    private ProjectRole memberRoleOrNull(final BigInteger projectId, final String userId) {
        return jdbc.query("SELECT role FROM ontology.og_project_member WHERE project_id = ? AND user_id = ?",
                        (rs, n) -> ProjectRole.valueOf(rs.getString(1)), projectId, userId)
                .stream().findFirst().orElse(null);
    }

    private boolean memberExists(final BigInteger projectId, final String userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_project_member WHERE project_id = ? AND user_id = ?",
                Integer.class, projectId, userId);
        return count != null && count > 0;
    }

    private BigInteger invitationProject(final UUID id) {
        return jdbc.query("SELECT project_id FROM ontology.og_project_invitation WHERE id = ?",
                        (rs, n) -> rs.getBigDecimal(1).toBigInteger(), id)
                .stream().findFirst().orElseThrow(ProjectMembershipService::denied);
    }

    private Invitation lockInvitation(final UUID id) {
        return jdbc.query("SELECT project_id, inviter_user_id, inviter_role, inviter_system_admin, target_user_id, role, status, expires_time "
                        + "FROM ontology.og_project_invitation WHERE id = ? FOR UPDATE",
                (rs, n) -> new Invitation(rs.getBigDecimal("project_id").toBigInteger(), rs.getString("inviter_user_id"),
                        roleOrNull(rs.getString("inviter_role")), rs.getBoolean("inviter_system_admin"),
                        rs.getString("target_user_id"), ProjectRole.valueOf(rs.getString("role")), rs.getString("status"),
                        rs.getTimestamp("expires_time").toInstant()), id)
                .stream().findFirst().orElseThrow(ProjectMembershipService::denied);
    }

    private void setInvitationStatus(final UUID id, final String status) {
        jdbc.update("UPDATE ontology.og_project_invitation SET status = ?, responded_time = NOW() WHERE id = ?",
                status, id);
    }

    private ProjectInvitationView invitationById(final UUID id) {
        return jdbc.query(invitationSelect() + " WHERE i.id = ?", ProjectMembershipService::mapInvitation, id)
                .stream().findFirst().orElseThrow(ProjectMembershipService::denied);
    }

    private static String invitationSelect() {
        return "SELECT i.id, p.name AS project_name, i.inviter_user_id, iu.display_name AS inviter_display, "
                + "i.target_user_id, tu.display_name AS target_display, i.role, i.status, i.created_time, i.expires_time "
                + "FROM ontology.og_project_invitation i JOIN ontology.og_organization p ON p.id = i.project_id "
                + "LEFT JOIN ontology.og_user iu ON iu.id = i.inviter_user_id "
                + "JOIN ontology.og_user tu ON tu.id = i.target_user_id";
    }

    private static ProjectInvitationView mapInvitation(final java.sql.ResultSet rs, final int rowNum)
            throws java.sql.SQLException {
        return new ProjectInvitationView(rs.getObject("id", UUID.class), rs.getString("project_name"),
                rs.getString("inviter_user_id"), rs.getString("inviter_display"), rs.getString("target_user_id"),
                rs.getString("target_display"), ProjectRole.valueOf(rs.getString("role")), rs.getString("status"),
                rs.getTimestamp("created_time").toInstant(), rs.getTimestamp("expires_time").toInstant());
    }

    private void audit(final String event, final String actor, final BigInteger projectId, final String capability,
                       final String key, final String value) {
        // 审计字段使用参数化 jsonb_build_object，避免拼接请求正文或未转义 JSON。
        jdbc.update("INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES (?, ?, jsonb_build_object('projectId', ?::text, 'capability', ?, ?, ?))",
                event, actor, projectId.toString(), capability, key, value);
    }

    private static long offset(final int page, final int size) {
        return (long) (page - 1) * size;
    }

    private static void validatePage(final int page, final int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) throw new OntologyApiException(ApiErrorCode.REQUEST_INVALID);
    }

    static String escapeLike(final String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ProjectRole roleOrNull(final String role) {
        return role == null ? null : ProjectRole.valueOf(role);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Project access denied");
    }

    private record Invitation(BigInteger projectId, String inviterId, ProjectRole inviterRole,
                              boolean inviterSystemAdmin, String targetId, ProjectRole role,
                              String status, Instant expiresTime) {
    }

    private record InviterState(boolean enabled, boolean systemAdmin) {
    }
}
