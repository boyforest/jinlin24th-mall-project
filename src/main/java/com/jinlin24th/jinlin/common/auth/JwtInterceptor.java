package com.jinlin24th.jinlin.common.auth;

import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;

    public JwtInterceptor(JwtUtil jwtUtil, AuthSessionService authSessionService) {
        this.jwtUtil = jwtUtil;
        this.authSessionService = authSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 预检请求直接放行（避免跨域场景被拦截）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        String token = extractBearerToken(authorization);
        if (!jwtUtil.validateToken(token)) {
            throw BizException.unauthorized("登录已失效");
        }

        String tokenType = jwtUtil.getTokenType(token);
        if (tokenType != null && !JwtUtil.TOKEN_TYPE_USER.equals(tokenType)) {
            throw BizException.unauthorized("登录已失效");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String jti = jwtUtil.getJtiFromToken(token);
        // 登录态增强校验：Redis 中保存的 jti 必须与 token 的 jti 一致
        authSessionService.validate(userId, jti);
        // 写入 ThreadLocal，供 @CurrentUserId 参数解析器使用
        CurrentUserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 重要：线程复用，必须清理 ThreadLocal，避免串号
        CurrentUserContext.clear();
    }

    private static String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw BizException.unauthorized("未登录");
        }

        String normalized = authorization.trim();
        if (!normalized.regionMatches(true, 0, "Bearer", 0, "Bearer".length())
                || normalized.length() == "Bearer".length()
                || !Character.isWhitespace(normalized.charAt("Bearer".length()))) {
            throw BizException.unauthorized("Authorization 必须使用 Bearer 格式");
        }

        String token = normalized.substring("Bearer".length()).trim();
        if (token.isBlank()) {
            throw BizException.unauthorized("未登录");
        }
        return token;
    }
}
