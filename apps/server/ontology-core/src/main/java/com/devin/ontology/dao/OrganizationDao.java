package com.devin.ontology.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devin.ontology.domain.entity.Organization;
import com.devin.ontology.domain.vo.OrganizationQueryVO;
import com.devin.ontology.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;

/**
 * 2025/11/16 16:00:30.
 *
 * <p>
 *  组织项目(Organization)Dao层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDao extends ServiceImpl<OrganizationMapper, Organization> {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 更新组织项目时间.
     * @param organizationId 组织项目ID
     */
    public void updateTime(final BigInteger organizationId) {
        lambdaUpdate()
                .eq(Organization::getId, organizationId)
                .set(Organization::getUpdateTime, new Date())
                .update();
    }

    /** 项目创建时原子写入创建者 OWNER 成员关系。 */
    public void insertOwnerMembership(final BigInteger projectId, final String userId) {
        jdbcTemplate.update(
                "INSERT INTO ontology.og_project_member (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
                projectId, userId);
    }

    /** 删除项目时清理其成员关系，调用方负责事务和项目授权。 */
    public void deleteMemberships(final BigInteger projectId) {
        jdbcTemplate.update("DELETE FROM ontology.og_project_member WHERE project_id = ?", projectId);
    }

    /** 写操作先锁定项目行；成员关系写操作也应遵循同一锁顺序。 */
    public void lockProjectForUpdate(final BigInteger projectId) {
        List<BigInteger> rows = jdbcTemplate.query(
                "SELECT id FROM ontology.og_organization WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getBigDecimal(1).toBigInteger(), projectId);
        if (rows.isEmpty()) {
            throw new AccessDeniedException("Project access denied");
        }
    }

    /** 审计与项目修改处于同一事务，详情只记录项目标识和对应能力。 */
    public void insertProjectAudit(final String eventType, final String actorUserId,
                                   final BigInteger projectId, final String capability) {
        String details = "{\"projectId\":\"" + projectId + "\",\"capability\":\"" + capability + "\"}";
        jdbcTemplate.update(
                "INSERT INTO ontology.og_audit_event (event_type, actor_user_id, details_json) "
                        + "VALUES (?, ?, CAST(? AS jsonb))",
                eventType, actorUserId, details);
    }

    /**
     * 分页查询组织项目.
     * @param queryVO 查询参数
     * @return 组织项目列表
     */
    public Page<Organization> pageByQuery(final OrganizationQueryVO queryVO,
                                            final String userId,
                                            final boolean systemAdmin) {
        long pageNum = queryVO.getPageNum() == null ? 1 : Math.max(1, queryVO.getPageNum());
        long pageSize = queryVO.getPageSize() == null ? 10 : Math.max(1, queryVO.getPageSize());
        long offset = (pageNum - 1) * pageSize;
        String keyword = queryVO.getKeyword();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String scope = systemAdmin ? "1 = 1" : "EXISTS (SELECT 1 FROM ontology.og_project_member m "
                + "WHERE m.project_id = p.id AND m.user_id = ?)";
        String filter = scope + (hasKeyword
                ? " AND (p.name ILIKE ? ESCAPE E'\\\\' OR p.id::text = ?)"
                : "");
        String fromWhere = " FROM ontology.og_organization p WHERE " + filter;
        Object[] scopeArgs = systemAdmin ? new Object[0] : new Object[]{userId};
        Object[] queryArgs;
        if (hasKeyword) {
            queryArgs = new Object[scopeArgs.length + 2];
            System.arraycopy(scopeArgs, 0, queryArgs, 0, scopeArgs.length);
            queryArgs[scopeArgs.length] = "%" + escapeLike(keyword.trim()) + "%";
            queryArgs[scopeArgs.length + 1] = keyword.trim();
        } else {
            queryArgs = scopeArgs;
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + fromWhere, Long.class, queryArgs);
        Object[] pageArgs = new Object[queryArgs.length + 2];
        System.arraycopy(queryArgs, 0, pageArgs, 0, queryArgs.length);
        pageArgs[queryArgs.length] = pageSize;
        pageArgs[queryArgs.length + 1] = offset;
        List<Organization> rows = jdbcTemplate.query(
                "SELECT p.id, p.name, p.description, p.create_time, p.update_time" + fromWhere
                        + " ORDER BY p.update_time DESC, p.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    Organization organization = new Organization();
                    organization.setId(rs.getBigDecimal("id").toBigInteger());
                    organization.setName(rs.getString("name"));
                    organization.setDescription(rs.getString("description"));
                    organization.setCreateTime(rs.getTimestamp("create_time"));
                    organization.setUpdateTime(rs.getTimestamp("update_time"));
                    return organization;
                }, pageArgs);
        Page<Organization> page = new Page<>(pageNum, pageSize, total == null ? 0 : total);
        page.setRecords(rows);
        return page;
    }

    /** 搜索文本按字面匹配，用户输入中的 %、_ 不会意外扩大结果集。 */
    private static String escapeLike(final String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
