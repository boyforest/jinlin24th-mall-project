package com.jinlin24th.jinlin.common.rate;

import com.jinlin24th.jinlin.common.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录限流的 IP 取值安全属性测试。
 * <p>
 * 登录限流是抵御后台口令爆破的最后一道自动化防线（10 次/分钟）。
 * nginx 用 {@code $proxy_add_x_forwarded_for} 追加真实 IP，若代码取**首值**，
 * 攻击者只需每次请求换一个伪造的 {@code X-Forwarded-For} 就能无成本爆破。
 * <p>
 * 本测试断言限流 key 中使用的是最右侧（真实）IP。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginRateLimitServiceTest {

    @Mock
    private RedisUtil redisUtil;

    private LoginRateLimitService service;

    @BeforeEach
    void setUp() {
        service = new LoginRateLimitService(redisUtil);
        when(redisUtil.incrementWithExpire(any(), anyLong(), any(Duration.class))).thenReturn(1L);
    }

    private HttpServletRequest requestWithXff(String xff) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xff);
        when(request.getRemoteAddr()).thenReturn("172.18.0.1");
        return request;
    }

    private String captureKey() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisUtil).incrementWithExpire(keyCaptor.capture(), anyLong(), any(Duration.class));
        return keyCaptor.getValue();
    }

    @Test
    @DisplayName("用户登录限流：必须用最右侧真实 IP，不能采信客户端伪造的首值")
    void userLoginLimitShouldUseRightmostIp() {
        service.checkUserLogin(requestWithXff("1.2.3.4, 203.0.113.9"));

        String key = captureKey();
        assertTrue(key.endsWith("203.0.113.9"),
            "限流 key 必须基于真实 IP，否则换伪造首值即可绕过爆破防护：" + key);
        assertTrue(!key.contains("1.2.3.4"),
            "绝不能把攻击者提供的 IP 用于限流 key：" + key);
    }

    @Test
    @DisplayName("后台登录限流：必须用最右侧真实 IP（后台爆破是高危路径）")
    void adminLoginLimitShouldUseRightmostIp() {
        service.checkAdminLogin(requestWithXff("1.2.3.4, 203.0.113.9"), "admin");

        String key = captureKey();
        assertTrue(key.contains("203.0.113.9"),
            "后台登录限流必须基于真实 IP：" + key);
        assertTrue(!key.contains("1.2.3.4"),
            "绝不能把攻击者提供的 IP 用于限流 key：" + key);
        assertTrue(key.contains("admin"), "key 应包含账号维度：" + key);
    }

    @Test
    @DisplayName("伪造不同的首值时，限流 key 必须保持稳定（无法通过换 IP 绕过）")
    void spoofedIpsMustNotChangeTheLimitKey() {
        service.checkUserLogin(requestWithXff("1.1.1.1, 203.0.113.9"));
        service.checkUserLogin(requestWithXff("2.2.2.2, 203.0.113.9"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisUtil, times(2)).incrementWithExpire(keyCaptor.capture(), anyLong(), any(Duration.class));
        var keys = keyCaptor.getAllValues();

        assertEquals(keys.get(0), keys.get(1),
            "同一真实 IP 下伪造不同首值不应产生不同限流 key，否则可无限绕过："
                + keys.get(0) + " vs " + keys.get(1));
    }
}
