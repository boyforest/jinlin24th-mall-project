package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentAdminId;
import com.jinlin24th.jinlin.common.auth.CurrentUserContext;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.rate.LoginRateLimitService;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.AdminLoginDTO;
import com.jinlin24th.jinlin.pojo.dto.AdminPasswordDTO;
import com.jinlin24th.jinlin.pojo.vo.AdminLoginVO;
import com.jinlin24th.jinlin.pojo.vo.AdminOptionVO;
import com.jinlin24th.jinlin.service.SysAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端认证控制器。
 * <p>
 * 后台账号从 sys_admin 表读取，密码使用 BCrypt 校验，登录成功后继续使用 JWT + Redis 登录态。
 */
@RestController
@RequestMapping("/admin")
public class AdminAuthController {

    private final SysAdminService sysAdminService;
    private final LoginRateLimitService loginRateLimitService;

    public AdminAuthController(
        SysAdminService sysAdminService,
        LoginRateLimitService loginRateLimitService
    ) {
        this.sysAdminService = sysAdminService;
        this.loginRateLimitService = loginRateLimitService;
    }

    /**
     * 管理端登录入口。
     * <p>
     * 限流放在 Controller 层，登录核心校验放在 Service 层，便于后续复用和测试。
     */
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@RequestBody AdminLoginDTO dto, HttpServletRequest request) {
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw BizException.badRequest("参数错误");
        }

        String username = dto.getUsername().trim();
        loginRateLimitService.checkAdminLogin(request, username);
        return Result.success(sysAdminService.login(dto, request));
    }

    /**
     * 管理员修改密码。
     * <p>
     * 用于首次登录强制改密以及后台自主改密。受限 token 仅能访问此接口。
     */
    @PutMapping("/password")
    public Result<AdminLoginVO> changePassword(
            @CurrentAdminId Long adminId,
            @RequestBody AdminPasswordDTO dto) {
        String adminName = CurrentUserContext.getAdminName();
        return Result.success(sysAdminService.changePassword(adminId, adminName, dto));
    }

    @GetMapping("/admins/options")
    public Result<List<AdminOptionVO>> adminOptions(@RequestParam(required = false) String keyword) {
        return Result.success(sysAdminService.listOptions(keyword));
    }
}
