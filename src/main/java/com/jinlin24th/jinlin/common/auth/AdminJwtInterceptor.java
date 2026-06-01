package com.jinlin24th.jinlin.common.auth;

import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminJwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;

    public AdminJwtInterceptor(JwtUtil jwtUtil, AuthSessionService authSessionService) {
        this.jwtUtil = jwtUtil;
        this.authSessionService = authSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        String token = extractBearerToken(authorization);
        if (!jwtUtil.validateToken(token)) {
            throw BizException.unauthorized("登录已失效");
        }

        String tokenType = jwtUtil.getTokenType(token);
        if (!JwtUtil.TOKEN_TYPE_ADMIN.equals(tokenType)) {
            throw BizException.forbidden("无权限");
        }

        String adminName = jwtUtil.getSubjectFromToken(token);
        Long adminId = jwtUtil.getAdminIdFromToken(token);
        String jti = jwtUtil.getJtiFromToken(token);
        authSessionService.validateAdmin(adminName, jti);

        // 写入 ThreadLocal，供 @CurrentAdminId 注解使用
        CurrentUserContext.setAdminId(adminId);

        return true;
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
