package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.AuthSessionService;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.rate.LoginRateLimitService;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.common.util.JwtUtil;
import com.jinlin24th.jinlin.pojo.dto.AdminLoginDTO;
import com.jinlin24th.jinlin.pojo.vo.AdminLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminAuthController {

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;
    private final LoginRateLimitService loginRateLimitService;

    public AdminAuthController(
        JwtUtil jwtUtil,
        AuthSessionService authSessionService,
        LoginRateLimitService loginRateLimitService
    ) {
        this.jwtUtil = jwtUtil;
        this.authSessionService = authSessionService;
        this.loginRateLimitService = loginRateLimitService;
    }

    @PostMapping("/login")
    public Result<AdminLoginVO> login(@RequestBody AdminLoginDTO dto, HttpServletRequest request) {
        if (!StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException("admin.password 不能为空");
        }
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw BizException.badRequest("参数错误");
        }

        String username = dto.getUsername().trim();
        loginRateLimitService.checkAdminLogin(request, username);

        if (!adminUsername.equals(username) || !adminPassword.equals(dto.getPassword())) {
            throw BizException.unauthorized("账号或密码错误");
        }

        String jti = jwtUtil.generateJti();
        String token = jwtUtil.generateAdminToken(username, jti);
        authSessionService.onLoginAdmin(username, jti);

        AdminLoginVO vo = new AdminLoginVO();
        vo.setUsername(username);
        vo.setToken(token);
        return Result.success(vo);
    }
}
