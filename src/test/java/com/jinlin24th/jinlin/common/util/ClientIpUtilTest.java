package com.jinlin24th.jinlin.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 客户端 IP 解析的安全属性测试。
 * <p>
 * 核心回归点：nginx 使用 {@code $proxy_add_x_forwarded_for}（追加而非覆盖），
 * 因此客户端自带的 {@code X-Forwarded-For: 1.2.3.4} 会被改写成
 * {@code 1.2.3.4, 真实IP}。旧实现取**首值**，等于采信攻击者提供的 IP，
 * 换个伪造 IP 就能绕过短信/登录的 IP 维度限流。
 * <p>
 * 本测试断言：必须取**最右侧**（由可信反向代理追加、外部无法伪造）的值。
 */
class ClientIpUtilTest {

    private HttpServletRequest request(String xff, String realIp, String remoteAddr) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
        when(req.getHeader("X-Real-IP")).thenReturn(realIp);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
        return req;
    }

    @Test
    @DisplayName("客户端伪造 XFF 首值时必须取最右侧的真实 IP（限流绕过回归）")
    void shouldIgnoreSpoofedFirstValueInForwardedFor() {
        // 攻击者发送 X-Forwarded-For: 1.2.3.4，nginx 追加真实 IP 203.0.113.9
        String ip = ClientIpUtil.resolve(request("1.2.3.4, 203.0.113.9", null, "172.18.0.1"));

        assertEquals("203.0.113.9", ip,
            "取首值意味着攻击者换一个伪造 IP 即可绕过限流");
    }

    @Test
    @DisplayName("多级代理链时必须取最右侧（最后一段）")
    void shouldTakeRightmostValueInProxyChain() {
        String ip = ClientIpUtil.resolve(request("10.0.0.1, 10.0.0.2, 203.0.113.9", null, "172.18.0.1"));

        assertEquals("203.0.113.9", ip);
    }

    @Test
    @DisplayName("只有一个 XFF 值时直接使用")
    void shouldUseSingleForwardedForValue() {
        String ip = ClientIpUtil.resolve(request("203.0.113.9", null, "172.18.0.1"));

        assertEquals("203.0.113.9", ip);
    }

    @Test
    @DisplayName("没有 XFF 时回退到 X-Real-IP")
    void shouldFallBackToRealIp() {
        String ip = ClientIpUtil.resolve(request(null, "203.0.113.9", "172.18.0.1"));

        assertEquals("203.0.113.9", ip);
    }

    @Test
    @DisplayName("XFF 与 X-Real-IP 都缺失时回退到 remoteAddr")
    void shouldFallBackToRemoteAddr() {
        String ip = ClientIpUtil.resolve(request(null, null, "203.0.113.9"));

        assertEquals("203.0.113.9", ip);
    }

    @Test
    @DisplayName("最右侧值为 unknown 时应继续回退，不能把 unknown 当 IP")
    void shouldSkipUnknownPlaceholder() {
        String ip = ClientIpUtil.resolve(request("1.2.3.4, unknown", "203.0.113.9", "172.18.0.1"));

        assertEquals("203.0.113.9", ip);
    }

    @Test
    @DisplayName("XFF 带多余空格时应 trim")
    void shouldTrimWhitespace() {
        String ip = ClientIpUtil.resolve(request("1.2.3.4,   203.0.113.9   ", null, "172.18.0.1"));

        assertEquals("203.0.113.9", ip);
    }

    @Test
    @DisplayName("request 为 null 时返回 unknown，不抛异常（保证限流链路可用）")
    void shouldReturnUnknownForNullRequest() {
        assertEquals("unknown", ClientIpUtil.resolve(null));
    }

    @Test
    @DisplayName("所有来源都为空时返回 unknown")
    void shouldReturnUnknownWhenAllSourcesBlank() {
        assertEquals("unknown", ClientIpUtil.resolve(request("  ", "  ", "  ")));
    }
}
