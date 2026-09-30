package com.jinlin24th.jinlin.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析工具。
 * <p>
 * 背景：限流（短信、登录）与操作日志此前各自实现了一份"取 X-Forwarded-For 首值"的逻辑。
 * 而 nginx 使用的是 {@code $proxy_add_x_forwarded_for}（**追加**而非覆盖），
 * 于是形如 {@code X-Forwarded-For: 1.2.3.4} 的客户端请求头会被改写成
 * {@code 1.2.3.4, 真实IP}——取首值等于直接采信攻击者提供的值，
 * 换一个伪造 IP 即可绕过所有 IP 维度限流（短信轰炸、后台爆破）。
 * <p>
 * 因此统一改为：**信任最右侧的值**（由我们自己的反向代理追加，客户端无法伪造），
 * 没有 XFF 时才退回 X-Real-IP / remoteAddr。部署侧同步把 nginx 改成覆盖式写入，
 * 两层同时收口。
 */
public final class ClientIpUtil {

    private static final String UNKNOWN = "unknown";

    private ClientIpUtil() {
    }

    /**
     * 解析客户端真实 IP，任何异常/缺失都回退到 {@code unknown}（不抛异常，保证限流链路可用）。
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // 取最右侧（最后一段）：由可信反向代理追加，最接近真实客户端且不可被外部伪造
            int lastComma = forwardedFor.lastIndexOf(',');
            String candidate = (lastComma >= 0 ? forwardedFor.substring(lastComma + 1) : forwardedFor).trim();
            if (!candidate.isEmpty() && !UNKNOWN.equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !UNKNOWN.equalsIgnoreCase(realIp.trim())) {
            return realIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? UNKNOWN : remoteAddr;
    }
}
