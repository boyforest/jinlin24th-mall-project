package com.jinlin24th.jinlin.common.rate;

import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class LoginRateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long LIMIT = 10;

    private final RedisUtil redisUtil;

    public LoginRateLimitService(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    public void checkUserLogin(HttpServletRequest request) {
        check("rl:login:user:" + getClientIp(request));
    }

    public void checkAdminLogin(HttpServletRequest request, String username) {
        String normalizedUsername = StringUtils.hasText(username) ? username.trim() : "unknown";
        check("rl:login:admin:" + getClientIp(request) + ":" + normalizedUsername);
    }

    private void check(String key) {
        long count = redisUtil.incrementWithExpire(key, 1, WINDOW);
        if (count > LIMIT) {
            throw new BizException(429, "请求过于频繁");
        }
    }

    private static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int idx = xff.indexOf(',');
            return (idx > 0 ? xff.substring(0, idx) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
