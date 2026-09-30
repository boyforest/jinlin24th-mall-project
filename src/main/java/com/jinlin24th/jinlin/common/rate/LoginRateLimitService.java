package com.jinlin24th.jinlin.common.rate;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.util.ClientIpUtil;
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
        check("ecommerce:rate-limit:login:user:" + getClientIp(request));
    }

    public void checkAdminLogin(HttpServletRequest request, String username) {
        String normalizedUsername = StringUtils.hasText(username) ? username.trim() : "unknown";
        check("ecommerce:rate-limit:login:admin:" + getClientIp(request) + ":" + normalizedUsername);
    }

    private void check(String key) {
        long count = redisUtil.incrementWithExpire(key, 1, WINDOW);
        if (count > LIMIT) {
            throw BizException.of(BizCode.RATE_LIMITED);
        }
    }

    /**
     * 获取客户端 IP。
     * <p>
     * 统一走 {@link ClientIpUtil}：取 X-Forwarded-For 的**最右侧**值。
     * 此前取首值等于采信攻击者伪造的 IP——每次请求换一个伪造值即可无限绕过
     * 登录限流（后台口令爆破的最后一道自动化防线）。
     */
    private static String getClientIp(HttpServletRequest request) {
        return ClientIpUtil.resolve(request);
    }
}
