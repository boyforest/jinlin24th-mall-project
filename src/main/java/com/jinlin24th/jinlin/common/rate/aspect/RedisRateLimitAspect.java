package com.jinlin24th.jinlin.common.rate.aspect;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.rate.annotation.RedisRateLimit;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Redis 接口限流切面。
 * <p>
 * 基于 Redis Lua 原子递增并设置过期时间，避免 incr 成功但 expire 失败导致 key 永不过期。
 */
@Slf4j
@Aspect
@Component
public class RedisRateLimitAspect {

    private final RedisUtil redisUtil;

    public RedisRateLimitAspect(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    /**
     * 拦截标注 @RedisRateLimit 的方法并执行限流判断。
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RedisRateLimit rateLimit) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String clientIp = clientIp();
        String businessKey = StringUtils.hasText(rateLimit.key())
            ? rateLimit.key()
            : method.getDeclaringClass().getSimpleName() + ":" + method.getName();
        String key = RedisUtil.KEY_PREFIX + "rate-limit:" + businessKey + ":" + clientIp;

        try {
            long count = redisUtil.incrementWithExpire(key, 1, Duration.ofSeconds(rateLimit.windowSeconds()));
            if (count > rateLimit.limit()) {
                log.warn("接口触发限流: key={}, count={}, limit={}", key, count, rateLimit.limit());
                throw BizException.of(BizCode.RATE_LIMITED);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("接口限流检查失败: key={}", key, e);
            throw BizException.of(BizCode.REDIS_ERROR);
        }

        return joinPoint.proceed();
    }

    /**
     * 获取客户端 IP，优先兼容网关/代理头。
     */
    private String clientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int idx = xff.indexOf(',');
            return (idx > 0 ? xff.substring(0, idx) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }
}
