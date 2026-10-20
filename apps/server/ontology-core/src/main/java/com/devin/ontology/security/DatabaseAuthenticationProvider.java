package com.devin.ontology.security;

import com.devin.ontology.identity.IdentityRepository;
import com.devin.ontology.identity.InternalUser;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** 使用身份仓储验证密码，认证对象只保留不含凭据的用户主体验证快照。 */
final class DatabaseAuthenticationProvider implements AuthenticationProvider {
    private static final String DUMMY_PASSWORD_HASH = new Argon2PasswordEncoder(16, 32, 1, 65_536, 3)
            .encode("fixed-unusable-authentication-probe-value");
    private final IdentityRepository identities;
    private final PasswordEncoder passwordEncoder;

    DatabaseAuthenticationProvider(final IdentityRepository identities, final PasswordEncoder passwordEncoder) {
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(final Authentication authentication) {
        String username = authentication.getPrincipal() == null ? "" : authentication.getPrincipal().toString();
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        if (username.length() > 120 || password.length() > 1024) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        InternalUser candidate = identities.findByUsername(username).orElse(null);
        boolean usable = candidate != null && candidate.enabled();
        String hash = usable ? identities.findPasswordHash(candidate.id()).orElse(DUMMY_PASSWORD_HASH)
                : DUMMY_PASSWORD_HASH;
        boolean matches = passwordEncoder.matches(password, hash);
        if (!usable || !matches || DUMMY_PASSWORD_HASH.equals(hash)) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        InternalUser user = candidate;
        LoginUserPrincipal principal = new LoginUserPrincipal(user.id(), user.username(), user.displayName(),
                user.systemAdmin(), user.authVersion(), System.currentTimeMillis());
        List<SimpleGrantedAuthority> authorities = user.systemAdmin()
                ? List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")) : List.of();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    @Override
    public boolean supports(final Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
