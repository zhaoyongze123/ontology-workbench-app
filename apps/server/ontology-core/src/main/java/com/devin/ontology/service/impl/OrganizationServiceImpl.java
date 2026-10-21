package com.devin.ontology.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.common.utils.BeanCopyUtils;
import com.devin.ontology.common.utils.r.PageResult;
import com.devin.ontology.dao.GraphDao;
import com.devin.ontology.dao.OrganizationDao;
import com.devin.ontology.domain.entity.Graph;
import com.devin.ontology.domain.entity.Organization;
import com.devin.ontology.domain.vo.OrganizationSaveVO;
import com.devin.ontology.domain.vo.OrganizationUpdateVO;
import com.devin.ontology.domain.vo.OrganizationVO;
import com.devin.ontology.domain.vo.OrganizationQueryVO;
import com.devin.ontology.service.OrganizationService;
import com.devin.ontology.project.ProjectAccessService;
import com.devin.ontology.project.ProjectCapability;
import com.devin.ontology.common.utils.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 2025/11/16 16:00:33.
 *
 * <p>
 *  组织项目(Organization)ServiceImpl层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service("organizationService-v2")
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final GraphDao graphDao;

    private final OrganizationDao organizationDao;

    private final ProjectAccessService projectAccessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationVO saveOrganization(final OrganizationSaveVO organizationSaveVO) {
        // 项目归属只来自当前可信身份，不能接受前端传入的 user id。
        var user = projectAccessService.currentUser();
        Organization organization = BeanCopyUtils.copy(organizationSaveVO, Organization.class);
        organization.setId(IdGenerator.generateKey());
        organization.init();
        organizationDao.save(organization);
        // 项目创建和 OWNER 事实必须同事务提交，避免出现无人拥有的项目。
        organizationDao.insertOwnerMembership(organization.getId(), user.id());
        organizationDao.insertProjectAudit("PROJECT_CREATED", user.id(), organization.getId(), "CREATE_PROJECT");
        return BeanCopyUtils.copy(organization, OrganizationVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editOrganization(final OrganizationUpdateVO organizationUpdateVO) {
        projectAccessService.requireProject(organizationUpdateVO.getId(), ProjectCapability.EDIT_PROJECT);
        organizationDao.lockProjectForUpdate(organizationUpdateVO.getId());
        // 锁行后重新读取成员事实，覆盖先授权后等待锁期间发生的成员移除。
        projectAccessService.requireProject(organizationUpdateVO.getId(), ProjectCapability.EDIT_PROJECT);
        var user = projectAccessService.currentUser();
        Organization organization = BeanCopyUtils.copy(organizationUpdateVO, Organization.class);
        organization.update();
        organizationDao.insertProjectAudit("PROJECT_UPDATED", user.id(), organization.getId(), "EDIT_PROJECT");
    }

    @Override
    public PageResult<OrganizationVO> pageOrganization(final OrganizationQueryVO queryVO) {
        var user = projectAccessService.currentUser();
        // 权限条件在数据库分页及 count 前生效，不能先读全量项目再过滤。
        Page<Organization> organizationPage = organizationDao.pageByQuery(
                queryVO, user.id(), user.systemAdmin());
        List<OrganizationVO> organizationVOList = BeanCopyUtils.copy(organizationPage.getRecords(), OrganizationVO.class);
        // 查询总数
        List<BigInteger> organizationIdList = organizationVOList.stream().map(OrganizationVO::getId).toList();
        if (organizationIdList.isEmpty()) {
            return PageResult.of(organizationVOList, organizationPage);
        }
        List<Graph> graphList = graphDao.getByOrganizationIds(organizationIdList);
        if (!graphList.isEmpty()) {
            Map<BigInteger, Map<String, Long>> graphCountMap = graphList.stream()
                    .collect(
                            Collectors.groupingBy(Graph::getOrganizationId,
                                    Collectors.groupingBy(Graph::getType, Collectors.counting()))
                    );
            for (OrganizationVO organizationVO : organizationVOList) {
                BigInteger organizationId = organizationVO.getId();
                organizationVO.setOntologyGraphTotal(
                        Optional.ofNullable(graphCountMap.get(organizationId))
                                .map(map -> map.getOrDefault(GraphTypeEnum.ONTOLOGY.name(), 0L))
                                .orElse(0L)
                );
                organizationVO.setEntityGraphTotal(
                        Optional.ofNullable(graphCountMap.get(organizationId))
                                .map(map -> map.getOrDefault(GraphTypeEnum.ENTITY.name(), 0L))
                                .orElse(0L)
                );
            }
        } else {
            organizationVOList = organizationVOList.stream().peek(organizationVO -> {
                organizationVO.setOntologyGraphTotal(0L);
                organizationVO.setEntityGraphTotal(0L);
            }).toList();
        }
        return PageResult.of(organizationVOList, organizationPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrganization(final BigInteger organizationId) {
        projectAccessService.requireProject(organizationId, ProjectCapability.DELETE_PROJECT);
        organizationDao.lockProjectForUpdate(organizationId);
        projectAccessService.requireProject(organizationId, ProjectCapability.DELETE_PROJECT);
        var user = projectAccessService.currentUser();
        // 查询组织下是否含有图谱
        List<Graph> graphList = graphDao.getByOrganizationIds(Collections.singletonList(organizationId));
        if (!graphList.isEmpty()) {
            throw new OntologyApiException(ApiErrorCode.PROJECT_HAS_DEPENDENCIES);
        }
        organizationDao.deleteMemberships(organizationId);
        organizationDao.removeById(organizationId);
        organizationDao.insertProjectAudit("PROJECT_DELETED", user.id(), organizationId, "DELETE_PROJECT");
    }
}
