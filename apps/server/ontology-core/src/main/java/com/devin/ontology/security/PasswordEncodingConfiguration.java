package com.devin.ontology.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 密码散列策略供身份凭据创建和运行时登录统一使用。 */
@Configuration
public class PasswordEncodingConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65_536, 3);
    }
}
