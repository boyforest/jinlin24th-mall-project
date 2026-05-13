package com.jinlin24th.jinlin.common.auth;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录态增强（JWT + Redis）
 * <p>
 * JWT 本身是“自包含”的：签发后只要不过期就一直有效，无法主动让某个 token 失效。
 * 引入 Redis 后，我们可以做到：
 * - 登出：立即失效
 * - 踢下线：立即失效
 * - 单点登录：新 token 覆盖旧 token，旧 token 立即失效
 * <p>
 * 实现方式：给 JWT 加一个 jti（token 唯一标识），并在 Redis 中保存“当前用户最新的 jti”。
 */
@Component
public class AuthSessionService {

    private final RedisUtil redisUtil;
    private final Duration tokenTtl;

    public AuthSessionService(RedisUtil redisUtil, @Value("${jwt.expiration}") long expirationMillis) {
        this.redisUtil = redisUtil;
        this.tokenTtl = Duration.ofMillis(expirationMillis);
    }

    /**
     * 用户登录成功后写入 Redis：auth:user:{userId}:jti -> jti（带 TTL）
     */
    public void onLogin(Long userId, String jti) {
        if (userId == null || jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("userId/jti 不能为空");
        }
        boolean ok = redisUtil.set(buildJtiKey(userId), jti, tokenTtl);
        // 登录态属于关键路径：写失败建议直接失败，避免“发了 token 但 Redis 没写进去”导致鉴权混乱
        if (!ok) {
            throw BizException.of(BizCode.AUTH_SESSION_WRITE_FAILED);
        }
    }

    /**
     * 登出：删除 Redis 中的登录态标记
     */
    public void onLogout(Long userId) {
        if (userId == null) {
            return;
        }
        redisUtil.deleteRequiredForAuth(buildJtiKey(userId));
    }

    public void onLoginAdmin(String adminName, String jti) {
        if (adminName == null || adminName.isBlank() || jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("adminName/jti 不能为空");
        }
        boolean ok = redisUtil.set(buildAdminJtiKey(adminName.trim()), jti, tokenTtl);
        if (!ok) {
            throw BizException.of(BizCode.AUTH_SESSION_WRITE_FAILED);
        }
    }

    public void onLogoutAdmin(String adminName) {
        if (adminName == null || adminName.isBlank()) {
            return;
        }
        redisUtil.deleteRequiredForAuth(buildAdminJtiKey(adminName.trim()));
    }

    /**
     * 鉴权时校验：token 的 jti 必须与 Redis 中保存的 jti 一致
     */
    public void validate(Long userId, String tokenJti) {
        if (userId == null || tokenJti == null || tokenJti.isBlank()) {
            throw BizException.unauthorized("登录已失效");
        }
        String savedJti = redisUtil.getRequiredForAuth(buildJtiKey(userId));
        if (savedJti == null || !savedJti.equals(tokenJti)) {
            // savedJti 为 null：可能用户登出 / Redis 过期 / Redis 被清空
            // 不一致：可能用户重新登录（单点登录场景），旧 token 立即失效
            throw BizException.unauthorized("登录已失效");
        }
    }

    private static String buildJtiKey(Long userId) {
        return "auth:user:" + userId + ":jti";
    }

    private static String buildAdminJtiKey(String adminName) {
        return "auth:admin:" + adminName + ":jti";
    }

    public void validateAdmin(String adminName, String tokenJti) {
        if (adminName == null || adminName.isBlank() || tokenJti == null || tokenJti.isBlank()) {
            throw BizException.unauthorized("登录已失效");
        }
        String savedJti = redisUtil.getRequiredForAuth(buildAdminJtiKey(adminName.trim()));
        if (savedJti == null || !savedJti.equals(tokenJti)) {
            throw BizException.unauthorized("登录已失效");
        }
    }
}
