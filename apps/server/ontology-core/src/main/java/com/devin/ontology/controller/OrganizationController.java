package com.devin.ontology.controller;

import com.devin.ontology.common.utils.r.Addition;
import com.devin.ontology.common.utils.r.ApiResult;
import com.devin.ontology.common.utils.r.PageResult;
import com.devin.ontology.domain.vo.OrganizationSaveVO;
import com.devin.ontology.domain.vo.OrganizationUpdateVO;
import com.devin.ontology.domain.vo.OrganizationVO;
import com.devin.ontology.domain.vo.OrganizationQueryVO;
import com.devin.ontology.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/16 16:00:31.
 *
 * <p>
 *  组织项目(Organization)Controller层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "organization")
@RestController
@RequiredArgsConstructor
@RequestMapping("/organization")
public class OrganizationController {
    
    private final OrganizationService organizationService;

    /**
     * 新建项目.
     *
     * @param organizationSaveVO 新建项目信息
     * @return 新建项目信息
     */
    @PostMapping("/save")
    @Operation(summary = "新建项目", description = "新建项目")
    public ApiResult<OrganizationVO> saveOrganization(@RequestBody @Valid final OrganizationSaveVO organizationSaveVO) {
        return ApiResult.success(organizationService.saveOrganization(organizationSaveVO));
    }

    /**
     * 修改项目.
     *
     * @param organizationUpdateVO 修改项目信息
     * @return 修改项目信息
     */
    @PutMapping("/edit")
    @Operation(summary = "修改项目", description = "修改项目")
    public ApiResult<Void> editOrganization(@RequestBody @Valid final OrganizationUpdateVO organizationUpdateVO) {
        organizationService.editOrganization(organizationUpdateVO);
        return ApiResult.success();
    }

    /**
     * 分页查询项目.
     *
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @return 项目列表
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询项目", description = "分页查询项目")
    public ApiResult<List<OrganizationVO>> organizationPage(
            @Parameter(name = "pageNum", description = "当前页码") @RequestParam(name = "pageNum", required = false) final Integer pageNum,
            @Parameter(name = "pageSize", description = "每页数量") @RequestParam(name = "pageSize", required = false) final Integer pageSize
    ) {
        OrganizationQueryVO queryVO = new OrganizationQueryVO();
        queryVO.setPageNum(pageNum);
        queryVO.setPageSize(pageSize);

        PageResult<OrganizationVO> pageResult = organizationService.pageOrganization(queryVO);
        Addition addition = Addition.of(pageResult);
        return ApiResult.success(pageResult.getRecords(), addition);
    }

    /**
     * 删除项目.
     *
     * @param organizationId 项目删除Id
     * @return 删除结果
     */
    @PostMapping("/delete")
    @Operation(summary = "删除项目", description = "删除项目")
    public ApiResult<Void> deleteOrganization(
           @Parameter(name = "organizationId", description = "项目删除Id", required = true) @RequestParam(name = "organizationId", required = true) @NotNull final BigInteger organizationId
    ) {
        organizationService.deleteOrganization(organizationId);
        return ApiResult.success();
    }
}
