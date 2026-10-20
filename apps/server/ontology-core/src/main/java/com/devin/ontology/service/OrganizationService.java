package com.devin.ontology.service;

import com.devin.ontology.common.utils.r.PageResult;
import com.devin.ontology.domain.vo.OrganizationSaveVO;
import com.devin.ontology.domain.vo.OrganizationUpdateVO;
import com.devin.ontology.domain.vo.OrganizationVO;
import com.devin.ontology.domain.vo.OrganizationQueryVO;
import jakarta.validation.Valid;
import java.math.BigInteger;

/**
 * 2025/11/16 16:00:32.
 *
 * <p>
 *  组织项目(Organization)Service层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
public interface OrganizationService {

    /**
     * 新建项目.
     * @param organizationSaveVO 项目保存信息
     * @return 项目信息
     */
    OrganizationVO saveOrganization(@Valid OrganizationSaveVO organizationSaveVO);

    /**
     * 修改项目.
     * @param organizationUpdateVO 修改项目信息
     */
    void editOrganization(@Valid OrganizationUpdateVO organizationUpdateVO);

    /**
     * 分页查询项目.
     * @param queryVO 查询条件
     * @return 项目信息列表
     */
    PageResult<OrganizationVO> pageOrganization(OrganizationQueryVO queryVO);

    /**
     * 删除项目.
     * @param organizationId 项目id
     */
    void deleteOrganization(BigInteger organizationId);
}
