package com.devin.ontology.controller;

import com.devin.ontology.common.utils.r.Addition;
import com.devin.ontology.common.utils.r.ApiResult;
import com.devin.ontology.project.ProjectInvitationRequest;
import com.devin.ontology.project.ProjectInvitationView;
import com.devin.ontology.project.ProjectAccessView;
import com.devin.ontology.project.ProjectMemberView;
import com.devin.ontology.project.ProjectMembershipService;
import com.devin.ontology.project.ProjectPage;
import com.devin.ontology.project.ProjectRoleRequest;
import com.devin.ontology.project.ProjectTransferRequest;
import com.devin.ontology.project.ProjectUserOption;
import jakarta.validation.Valid;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目成员API只做请求校验、路由和统一响应封装。 */
@RestController
@RequiredArgsConstructor
public class ProjectMembershipController {
    private final ProjectMembershipService membershipService;

    @GetMapping("/projects/{projectId}/access")
    public ApiResult<ProjectAccessView> access(@PathVariable final BigInteger projectId) {
        return ApiResult.success(membershipService.access(projectId));
    }

    @GetMapping("/projects/{projectId}/members")
    public ApiResult<List<ProjectMemberView>> members(@PathVariable final BigInteger projectId,
            @RequestParam(defaultValue = "1") final int pageNum,
            @RequestParam(defaultValue = "20") final int pageSize) {
        return page(membershipService.members(projectId, pageNum, pageSize));
    }

    @PostMapping("/projects/{projectId}/invitations")
    public ApiResult<ProjectInvitationView> invite(@PathVariable final BigInteger projectId,
            @RequestBody @Valid final ProjectInvitationRequest request) {
        return ApiResult.success(membershipService.invite(projectId, request));
    }

    @GetMapping("/project-invitations/mine")
    public ApiResult<List<ProjectInvitationView>> mine(@RequestParam(defaultValue = "1") final int pageNum,
            @RequestParam(defaultValue = "20") final int pageSize) {
        return page(membershipService.mine(pageNum, pageSize));
    }

    @PostMapping("/project-invitations/{invitationId}/accept")
    public ApiResult<ProjectInvitationView> accept(@PathVariable final UUID invitationId) {
        return ApiResult.success(membershipService.accept(invitationId));
    }

    @PostMapping("/project-invitations/{invitationId}/decline")
    public ApiResult<ProjectInvitationView> decline(@PathVariable final UUID invitationId) {
        return ApiResult.success(membershipService.decline(invitationId));
    }

    @PutMapping("/projects/{projectId}/members/{userId}")
    public ApiResult<Void> changeRole(@PathVariable final BigInteger projectId, @PathVariable final String userId,
            @RequestBody @Valid final ProjectRoleRequest request) {
        membershipService.changeRole(projectId, userId, request.role());
        return ApiResult.success();
    }

    @DeleteMapping("/projects/{projectId}/members/{userId}")
    public ApiResult<Void> removeMember(@PathVariable final BigInteger projectId, @PathVariable final String userId) {
        membershipService.removeMember(projectId, userId);
        return ApiResult.success();
    }

    @PostMapping("/projects/{projectId}/leave")
    public ApiResult<Void> leave(@PathVariable final BigInteger projectId) {
        membershipService.leave(projectId);
        return ApiResult.success();
    }

    @PostMapping("/projects/{projectId}/transfer-owner")
    public ApiResult<Void> transferOwner(@PathVariable final BigInteger projectId,
            @RequestBody @Valid final ProjectTransferRequest request) {
        membershipService.transferOwner(projectId, request.targetUserId());
        return ApiResult.success();
    }

    @GetMapping("/users/search")
    public ApiResult<List<ProjectUserOption>> searchUsers(@RequestParam("q") final String query,
            @RequestParam(defaultValue = "1") final int pageNum,
            @RequestParam(defaultValue = "20") final int pageSize) {
        return page(membershipService.searchUsers(query, pageNum, pageSize));
    }

    @GetMapping("/projects/{projectId}/invite-candidates")
    public ApiResult<List<ProjectUserOption>> inviteCandidates(@PathVariable final BigInteger projectId,
            @RequestParam(required = false) final String q,
            @RequestParam(defaultValue = "1") final int pageNum,
            @RequestParam(defaultValue = "20") final int pageSize) {
        return page(membershipService.inviteCandidates(projectId, q, pageNum, pageSize));
    }

    private static <T> ApiResult<List<T>> page(final ProjectPage<T> page) {
        Addition addition = new Addition();
        addition.setTotal((int) Math.min(Integer.MAX_VALUE, page.total()));
        addition.setPageNum((int) page.pageNum());
        addition.setPageSize((int) page.pageSize());
        return ApiResult.success(page.records(), addition);
    }
}
