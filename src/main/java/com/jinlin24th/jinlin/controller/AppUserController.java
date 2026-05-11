package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentUserId;
import com.jinlin24th.jinlin.common.auth.AuthSessionService;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.rate.LoginRateLimitService;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.common.util.JwtUtil;
import com.jinlin24th.jinlin.pojo.dto.UserLoginDTO;
import com.jinlin24th.jinlin.pojo.vo.AppUserVO;
import com.jinlin24th.jinlin.pojo.vo.UserLoginVO;
import com.jinlin24th.jinlin.service.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/appUser")
@Slf4j
public class AppUserController {

    private final AppUserService appUserService;
    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;
    private final LoginRateLimitService loginRateLimitService;

    public AppUserController(
        AppUserService appUserService,
        JwtUtil jwtUtil,
        AuthSessionService authSessionService,
        LoginRateLimitService loginRateLimitService
    ) {
        this.appUserService = appUserService;
        this.jwtUtil = jwtUtil;
        this.authSessionService = authSessionService;
        this.loginRateLimitService = loginRateLimitService;
    }

    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO dto, HttpServletRequest request) {
        loginRateLimitService.checkUserLogin(request);

        AppUserVO user = appUserService.login(dto);
        if (user == null || user.getId() == null) {
            throw BizException.badRequest("登录失败");
        }
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        // 登录态增强：
        // - 每次登录生成新的 jti
        // - token 中携带 jti
        // - Redis 记录该用户“当前有效 jti”（用于登出/踢下线/单点登录）
        String jti = jwtUtil.generateJti();
        String token = jwtUtil.generateToken(user.getId(), jti);
        authSessionService.onLogin(user.getId(), jti);

        vo.setToken(token);
        return Result.success(vo);
    }

    /**
     * 退出登录：删除 Redis 中保存的 jti，使当前 token 立即失效
     */
    @PostMapping("/logout")
    public Result<Boolean> logout(@CurrentUserId Long userId) {
        authSessionService.onLogout(userId);
        return Result.success(true);
    }

    /**
     * 获取当前登录用户信息
     *
     * 说明：C 端接口不要由客户端传 userId/id（避免越权），应从登录态获取
     */
    @GetMapping("/me")
    public Result<AppUserVO> me(@CurrentUserId Long userId) {
        return Result.success(appUserService.getUserInfo(userId));
    }
}


