package com.devin.ontology.security;

import com.devin.ontology.identity.IdentityRepository;
import com.devin.ontology.identity.InternalUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 每次请求核验账号状态，并按真实业务活动维护空闲超时。 */
final class SessionValidityFilter extends OncePerRequestFilter {
    static final String LAST_REAL_ACTIVITY = SessionValidityFilter.class.getName() + ".lastRealActivity";
    static final long IDLE_LIMIT_MILLIS = 30L * 60 * 1000;
    static final long ABSOLUTE_LIMIT_MILLIS = 8L * 60 * 60 * 1000;

    private final IdentityRepository identities;

    SessionValidityFilter(final IdentityRepository identities) {
        this.identities = identities;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginUserPrincipal principal) {
            long now = System.currentTimeMillis();
            HttpSession session = request.getSession(false);
            Object lastValue = session == null ? null : session.getAttribute(LAST_REAL_ACTIVITY);
            long lastActivity = lastValue instanceof Long value ? value : principal.loginAtEpochMillis();
            boolean expired = now - principal.loginAtEpochMillis() >= ABSOLUTE_LIMIT_MILLIS
                    || now - lastActivity >= IDLE_LIMIT_MILLIS;
            InternalUser user = expired ? null : identities.findById(principal.userId()).orElse(null);
            if (user == null || !user.enabled() || !Objects.equals(user.authVersion(), principal.authVersion())) {
                SecurityContextHolder.clearContext();
                if (session != null) session.invalidate();
            } else if (!isPassiveAuthProbe(request)) {
                session.setAttribute(LAST_REAL_ACTIVITY, now);
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPassiveAuthProbe(final HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "GET".equals(request.getMethod()) && ("/auth/me".equals(path) || "/auth/csrf".equals(path));
    }
}
