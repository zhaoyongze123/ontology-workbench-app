package com.devin.ontology.security;

import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.identity.IdentityRepository;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 业务层取得当前可信用户的统一入口。身份只从已验证的 SecurityContext 恢复。 */
@Component
public class CurrentIdentity {
    private final IdentityRepository identityRepository;

    public CurrentIdentity(final IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    public InternalUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUserPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("需要登录");
        }
        return identityRepository.findById(principal.userId())
                .filter(InternalUser::enabled)
                .filter(user -> Objects.equals(user.authVersion(), principal.authVersion()))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("登录状态已失效"));
    }
}
