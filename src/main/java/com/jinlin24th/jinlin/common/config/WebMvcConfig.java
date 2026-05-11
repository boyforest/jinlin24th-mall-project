package com.jinlin24th.jinlin.common.config;

import com.jinlin24th.jinlin.common.auth.CurrentUserIdArgumentResolver;
import com.jinlin24th.jinlin.common.auth.AdminJwtInterceptor;
import com.jinlin24th.jinlin.common.auth.JwtInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AdminJwtInterceptor adminJwtInterceptor;
    private final String corsAllowedOrigins;

    public WebMvcConfig(
        JwtInterceptor jwtInterceptor,
        AdminJwtInterceptor adminJwtInterceptor,
        @Value("${app.cors.allowed-origins:*}") String corsAllowedOrigins
    ) {
        this.jwtInterceptor = jwtInterceptor;
        this.adminJwtInterceptor = adminJwtInterceptor;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // 支持在 Controller 方法参数中使用 @CurrentUserId
        resolvers.add(new CurrentUserIdArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 用户端接口统一走 JWT 鉴权（登录接口放行）
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/user/**")
            .excludePathPatterns(
                "/user/appUser/login",
                "/user/product/**",
                "/user/product/category/**"
            );

        registry.addInterceptor(adminJwtInterceptor)
            .addPathPatterns("/admin/**")
            .excludePathPatterns("/admin/login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
        if (origins.length == 0) {
            origins = new String[] {"*"};
        }

        var reg = registry.addMapping("/**")
            .allowedMethods("*")
            .allowedHeaders("*")
            .exposedHeaders("Authorization")
            .allowCredentials(true);

        if (origins.length == 1 && "*".equals(origins[0])) {
            reg.allowedOriginPatterns("*");
        } else {
            reg.allowedOrigins(origins);
        }
    }
}
