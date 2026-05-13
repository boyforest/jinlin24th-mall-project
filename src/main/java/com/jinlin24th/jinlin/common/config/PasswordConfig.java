package com.jinlin24th.jinlin.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码安全配置。
 * <p>
 * 当前项目只使用 Spring Security Crypto 的 BCrypt 哈希能力，不接入完整 Spring Security 登录过滤链。
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt 会自动为每个密码生成随机盐，适合后台管理员密码存储。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
