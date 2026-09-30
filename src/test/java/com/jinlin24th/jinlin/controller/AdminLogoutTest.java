package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.AuthSessionService;
import com.jinlin24th.jinlin.common.auth.CurrentUserContext;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.rate.LoginRateLimitService;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.service.SysAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 管理端登出的服务端行为测试。
 * <p>
 * 修复的缺陷：管理端**没有登出接口**。`AuthSessionService.onLogoutAdmin()` 早已实现，
 * 但没有任何 Controller 调用它。前端"退出登录"只清 localStorage，服务端 Redis 里的
 * jti 仍然有效——token 一旦泄漏（XSS、日志、抓包），攻击者仍能继续使用整整 7 天
 * （`jwt.expiration=604800000`），且无法主动踢下线。
 * <p>
 * 本测试锁定 Controller 的真实行为，而不是 mock 的调用：
 * 登出必须用当前管理员身份调用服务端登出。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminLogoutTest {

    @Mock
    private SysAdminService sysAdminService;
    @Mock
    private LoginRateLimitService loginRateLimitService;
    @Mock
    private AuthSessionService authSessionService;

    private AdminAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAuthController(sysAdminService, loginRateLimitService, authSessionService);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("登出必须删除服务端登录态，而不是只让前端清 localStorage")
    void logoutShouldInvalidateServerSideSession() {
        CurrentUserContext.setAdminId(1L);
        CurrentUserContext.setAdminName("admin");

        Result<Boolean> result = controller.logout();

        verify(authSessionService).onLogoutAdmin("admin");
        assertTrue(result != null && Boolean.TRUE.equals(result.getData()),
            "登出应返回成功");
    }

    @Test
    @DisplayName("缺少管理员身份时不得静默成功（否则用户以为已登出、实际仍有效）")
    void logoutWithoutIdentityMustFailLoudly() {
        CurrentUserContext.clear();

        assertThrows(BizException.class, () -> controller.logout(),
            "拿不到身份就无法删除登录态，必须报错而不是返回成功");
        verify(authSessionService, never()).onLogoutAdmin(org.mockito.ArgumentMatchers.anyString());
    }
}
