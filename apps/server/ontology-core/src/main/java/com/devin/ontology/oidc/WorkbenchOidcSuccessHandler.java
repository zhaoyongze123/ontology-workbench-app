package com.devin.ontology.oidc;

import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.LoginUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;

/** 将经过 Spring OIDC 校验的主体映射为 Ontology 内部会话。 */
final class WorkbenchOidcSuccessHandler implements AuthenticationSuccessHandler {
    private static final String PENDING_BINDING = PendingWorkbenchBinding.SESSION_ATTRIBUTE;
    private static final String FRONTEND_RETURN_PATH = "/ontology-management/";
    // 与既有 SessionValidityFilter 的属性名保持一致，避免改动其包可见边界。
    private static final String LAST_REAL_ACTIVITY = "com.devin.ontology.security.SessionValidityFilter.lastRealActivity";
    private static final long RECENT_LOGIN_MILLIS = 10L * 60 * 1000;

    private final WorkbenchIdentityService identities;
    private final WorkbenchOidcProperties properties;
    private final ObjectMapper objectMapper;
    private final SecurityContextRepository securityContextRepository;

    WorkbenchOidcSuccessHandler(final WorkbenchIdentityService identities,
            final WorkbenchOidcProperties properties, final ObjectMapper objectMapper,
            final SecurityContextRepository securityContextRepository) {
        this.identities = identities;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    public void onAuthenticationSuccess(final HttpServletRequest request, final HttpServletResponse response,
            final Authentication authentication) throws IOException, ServletException {
        try {
            HttpSession session = request.getSession(false);
            Object pendingValue = session == null ? null : session.getAttribute(PENDING_BINDING);
            if (session != null && pendingValue != null) session.removeAttribute(PENDING_BINDING);
            if (pendingValue != null && !(pendingValue instanceof PendingWorkbenchBinding)) {
                reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, "workbench_login_failed");
                return;
            }
            PendingWorkbenchBinding pending = (PendingWorkbenchBinding) pendingValue;
            if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
                reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, "workbench_identity_invalid");
                return;
            }
            VerifiedWorkbenchIdentity external = verifiedIdentity(oidcUser);
            long now = System.currentTimeMillis();
            InternalUser user;
            if (pending != null) {
                if (pending.expiresAtEpochMillis() <= now
                        || pending.loginAtEpochMillis() > now
                        || now - pending.loginAtEpochMillis() > RECENT_LOGIN_MILLIS) {
                    reject(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                            "recent_authentication_required");
                    return;
                }
                user = identities.bind(external, pending.userId(), pending.authVersion());
            } else {
                user = identities.loginOrCreate(external);
            }
            if (!user.enabled()) {
                reject(request, response, HttpServletResponse.SC_FORBIDDEN, "account_disabled");
                return;
            }

            long loginAt = System.currentTimeMillis();
            LoginUserPrincipal principal = new LoginUserPrincipal(user.id(), user.username(), user.displayName(),
                    user.systemAdmin(), user.authVersion(), loginAt);
            List<SimpleGrantedAuthority> authorities = user.systemAdmin()
                    ? List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")) : List.of();
            Authentication internalAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, authorities);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(internalAuthentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            HttpSession authenticatedSession = request.getSession(true);
            authenticatedSession.setAttribute(LAST_REAL_ACTIVITY, loginAt);
            authenticatedSession.removeAttribute(PENDING_BINDING);
            // 登录完成后只回到固定前端路由，回调不接受任意 returnUrl/redirect 参数。
            response.sendRedirect(FRONTEND_RETURN_PATH);
        } catch (WorkbenchIdentityExceptions.BindingConflict conflict) {
            reject(request, response, HttpServletResponse.SC_CONFLICT, "workbench_identity_already_bound");
        } catch (WorkbenchIdentityExceptions.StaleInternalUser stale) {
            reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, "internal_identity_changed");
        } catch (WorkbenchIdentityExceptions.DisabledUser disabled) {
            reject(request, response, HttpServletResponse.SC_FORBIDDEN, "account_disabled");
        } catch (IllegalArgumentException invalidIdentity) {
            reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, "workbench_identity_invalid");
        } catch (RuntimeException failure) {
            // 异常文本可能含第三方响应或令牌，错误响应只暴露固定原因码。
            reject(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "workbench_authentication_unavailable");
        }
    }

    private VerifiedWorkbenchIdentity verifiedIdentity(final OidcUser user) {
        String issuer = user.getClaimAsString("iss");
        String subject = user.getSubject();
        String name = user.getClaimAsString("name");
        if (name == null || name.isBlank()) name = user.getClaimAsString("preferred_username");
        if (issuer == null || !issuer.equals(properties.getIssuer()) || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Workbench OIDC 声明无效");
        }
        // email 仅保留在此短暂声明对象里，不参与匹配、保存或审计。
        return new VerifiedWorkbenchIdentity(issuer, subject, name, user.getClaimAsString("email"));
    }

    private void writeError(final HttpServletResponse response, final int status, final String code)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("error", code));
    }

    /** OIDC 回调失败时删除会话，确保旧内部会话和临时授权状态都不能继续使用。 */
    private void reject(final HttpServletRequest request, final HttpServletResponse response,
            final int status, final String code) throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // 已删除的 session 不再存在可复用的持久 SecurityContext。
            }
        }
        writeError(response, status, code);
    }

}
