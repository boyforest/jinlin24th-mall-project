package com.jinlin24th.jinlin.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 临时拦截器：用于你前端/鉴权还没写完时的本地调试
 *
 * 用法：
 * - 请求头传 X-User-Id: 1
 * - 就会把当前用户ID写入 ThreadLocal
 *
 * 注意：正式接 JWT 后把这个删掉/替换为 JwtInterceptor
 */
@Slf4j
@Component
public class DemoLoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("X-User-Id");
        if (header != null && !header.isBlank()) {
            try {
                CurrentUserContext.setUserId(Long.parseLong(header));
            } catch (NumberFormatException e) {
                log.warn("非法的 X-User-Id: {}", header);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }
}

