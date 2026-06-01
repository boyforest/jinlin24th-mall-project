package com.jinlin24th.jinlin.common.config;

import com.jinlin24th.jinlin.common.auth.CurrentAdminIdArgumentResolver;
import com.jinlin24th.jinlin.common.auth.CurrentUserIdArgumentResolver;
import com.jinlin24th.jinlin.common.auth.AdminJwtInterceptor;
import com.jinlin24th.jinlin.common.auth.JwtInterceptor;
import com.jinlin24th.jinlin.common.log.OperationLogInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AdminJwtInterceptor adminJwtInterceptor;
    private final OperationLogInterceptor operationLogInterceptor;
    private final String corsAllowedOrigins;
    private final String uploadDir;

    public WebMvcConfig(
        JwtInterceptor jwtInterceptor,
        AdminJwtInterceptor adminJwtInterceptor,
        OperationLogInterceptor operationLogInterceptor,
        @Value("${app.cors.allowed-origins:*}") String corsAllowedOrigins,
        @Value("${app.upload.dir:uploads}") String uploadDir
    ) {
        this.jwtInterceptor = jwtInterceptor;
        this.adminJwtInterceptor = adminJwtInterceptor;
        this.operationLogInterceptor = operationLogInterceptor;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.uploadDir = uploadDir;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // 支持在 Controller 方法参数中使用 @CurrentUserId / @CurrentAdminId
        resolvers.add(new CurrentUserIdArgumentResolver());
        resolvers.add(new CurrentAdminIdArgumentResolver());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 用户端接口统一走 JWT 鉴权（登录接口放行）
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/user/**")
            .excludePathPatterns(
                "/user/appUser/login",
                "/user/product/**",
                "/user/product/category/**",
                "/user/marketing/**"
            );

        registry.addInterceptor(adminJwtInterceptor)
            .addPathPatterns("/admin/**")
            .excludePathPatterns("/admin/login");

        // 后台增删改操作日志：在鉴权后记录，便于从 token 中拿到管理员账号
        registry.addInterceptor(operationLogInterceptor)
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
