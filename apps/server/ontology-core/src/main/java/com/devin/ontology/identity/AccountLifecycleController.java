package com.devin.ontology.identity;

import com.devin.ontology.identity.AccountLifecycleService.ActionTokenInspection;
import com.devin.ontology.identity.AccountLifecycleService.ActionTokenRequest;
import com.devin.ontology.identity.AccountLifecycleService.AccountPage;
import com.devin.ontology.identity.AccountLifecycleService.CreatedAccount;
import com.devin.ontology.identity.AccountLifecycleService.CreateAccountRequest;
import com.devin.ontology.identity.AccountLifecycleService.EnabledRequest;
import com.devin.ontology.identity.AccountLifecycleService.PasswordActionRequest;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

/** 仅做 HTTP 契约转换；权限与账号状态变化均由事务服务负责。 */
@RestController
@Profile("multiuser")
public class AccountLifecycleController {
    private final AccountLifecycleService accounts;

    public AccountLifecycleController(final AccountLifecycleService accounts) {
        this.accounts = accounts;
    }

    @GetMapping(path = "/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountPage users(@RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return accounts.listUsers(page, size);
    }

    @PostMapping(path = "/admin/users", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CreatedAccount create(@RequestBody final CreateAccountRequest request,
            final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return accounts.createPendingUser(request);
    }

    @PatchMapping(path = "/admin/users/{userId}/enabled", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountUserView setEnabled(@PathVariable final String userId,
            @RequestBody final EnabledRequest request) {
        return accounts.setEnabled(userId, request.enabled());
    }

    @PostMapping(path = "/admin/users/{userId}/activation-link", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> issueActivationLink(@PathVariable final String userId,
            final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of("activationUrl", accounts.issueActivationLink(userId));
    }

    @PostMapping(path = "/admin/users/{userId}/password-reset-link",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> issueResetLink(@PathVariable final String userId,
            final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of("resetUrl", accounts.issuePasswordResetLink(userId));
    }

    @PostMapping(path = "/auth/account-actions/inspect", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ActionTokenInspection inspect(@RequestBody final ActionTokenRequest request,
            final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return accounts.inspectActionToken(request.token());
    }

    @PostMapping(path = "/auth/activate", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> activate(@RequestBody final PasswordActionRequest request,
            final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        accounts.activate(request.token(), request.newPassword());
        return Map.of("status", "activated");
    }

    @PostMapping(path = "/auth/password-reset", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> resetPassword(@RequestBody final PasswordActionRequest request,
            final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        accounts.resetPassword(request.token(), request.newPassword());
        return Map.of("status", "password_reset");
    }
}
