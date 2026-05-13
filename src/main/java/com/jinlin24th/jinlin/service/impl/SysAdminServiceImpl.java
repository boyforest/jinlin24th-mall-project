package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.common.auth.AuthSessionService;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.util.JwtUtil;
import com.jinlin24th.jinlin.mapper.SysAdminMapper;
import com.jinlin24th.jinlin.mapper.SysAdminRoleMapper;
import com.jinlin24th.jinlin.mapper.SysPermissionMapper;
import com.jinlin24th.jinlin.mapper.SysRoleMapper;
import com.jinlin24th.jinlin.mapper.SysRolePermissionMapper;
import com.jinlin24th.jinlin.pojo.dto.AdminLoginDTO;
import com.jinlin24th.jinlin.pojo.entity.SysAdmin;
import com.jinlin24th.jinlin.pojo.entity.SysAdminRole;
import com.jinlin24th.jinlin.pojo.entity.SysPermission;
import com.jinlin24th.jinlin.pojo.entity.SysRole;
import com.jinlin24th.jinlin.pojo.entity.SysRolePermission;
import com.jinlin24th.jinlin.pojo.vo.AdminLoginVO;
import com.jinlin24th.jinlin.service.SysAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统管理员服务实现。
 * <p>
 * 登录链路：数据库管理员档案 + BCrypt 密码校验 + JWT 签发 + Redis 登录态增强。
 */
@Slf4j
@Service
public class SysAdminServiceImpl extends ServiceImpl<SysAdminMapper, SysAdmin> implements SysAdminService {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;
    private final SysAdminRoleMapper sysAdminRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;

    public SysAdminServiceImpl(
        PasswordEncoder passwordEncoder,
        JwtUtil jwtUtil,
        AuthSessionService authSessionService,
        SysAdminRoleMapper sysAdminRoleMapper,
        SysRoleMapper sysRoleMapper,
        SysRolePermissionMapper sysRolePermissionMapper,
        SysPermissionMapper sysPermissionMapper
    ) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authSessionService = authSessionService;
        this.sysAdminRoleMapper = sysAdminRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysPermissionMapper = sysPermissionMapper;
    }

    /**
     * 管理端登录。
     * <p>
     * 账号不存在、密码错误统一返回“账号或密码错误”，避免泄露账号是否存在。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminLoginVO login(AdminLoginDTO dto, HttpServletRequest request) {
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw BizException.badRequest("账号和密码不能为空");
        }

        String username = dto.getUsername().trim();
        SysAdmin admin = lambdaQuery()
            .eq(SysAdmin::getUsername, username)
            .eq(SysAdmin::getDeleted, 0)
            .one();
        if (admin == null || !passwordEncoder.matches(dto.getPassword(), admin.getPasswordHash())) {
            throw BizException.unauthorized("账号或密码错误");
        }
        if (!Objects.equals(admin.getStatus(), 1)) {
            throw BizException.forbidden("管理员账号已被禁用");
        }

        // 登录成功后更新最后登录信息，方便后台安全审计。
        lambdaUpdate()
            .set(SysAdmin::getLastLoginTime, LocalDateTime.now())
            .set(SysAdmin::getLastLoginIp, getClientIp(request))
            .eq(SysAdmin::getId, admin.getId())
            .update();

        List<String> roles = getRoleCodes(admin.getId());
        List<String> permissions = getPermissionCodes(admin.getId());

        String jti = jwtUtil.generateJti();
        String token = jwtUtil.generateAdminToken(username, jti);
        authSessionService.onLoginAdmin(username, jti);

        AdminLoginVO vo = new AdminLoginVO();
        vo.setAdminId(admin.getId());
        vo.setUsername(username);
        vo.setRealName(admin.getRealName());
        vo.setToken(token);
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        return vo;
    }

    /**
     * 查询管理员已启用角色编码。
     */
    @Override
    public List<String> getRoleCodes(Long adminId) {
        if (adminId == null) {
            return Collections.emptyList();
        }
        List<Long> roleIds = sysAdminRoleMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysAdminRole>lambdaQuery()
                    .eq(SysAdminRole::getAdminId, adminId)
            ).stream()
            .map(SysAdminRole::getRoleId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysRoleMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysRole>lambdaQuery()
                    .in(SysRole::getId, roleIds)
                    .eq(SysRole::getStatus, 1)
                    .orderByAsc(SysRole::getSort)
            ).stream()
            .map(SysRole::getRoleCode)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    /**
     * 查询管理员已启用权限编码。
     */
    @Override
    public List<String> getPermissionCodes(Long adminId) {
        if (adminId == null) {
            return Collections.emptyList();
        }
        List<Long> roleIds = sysAdminRoleMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysAdminRole>lambdaQuery()
                    .eq(SysAdminRole::getAdminId, adminId)
            ).stream()
            .map(SysAdminRole::getRoleId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> enabledRoleIds = sysRoleMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysRole>lambdaQuery()
                    .in(SysRole::getId, roleIds)
                    .eq(SysRole::getStatus, 1)
            ).stream()
            .map(SysRole::getId)
            .collect(Collectors.toSet());
        if (enabledRoleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> permissionIds = sysRolePermissionMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysRolePermission>lambdaQuery()
                    .in(SysRolePermission::getRoleId, enabledRoleIds)
            ).stream()
            .map(SysRolePermission::getPermissionId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return sysPermissionMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysPermission>lambdaQuery()
                    .in(SysPermission::getId, permissionIds)
                    .orderByAsc(SysPermission::getSort)
            ).stream()
            .map(SysPermission::getPermissionCode)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    /**
     * 获取客户端 IP，兼容本地开发代理和上线后的反向代理。
     */
    private static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int idx = xff.indexOf(',');
            return (idx > 0 ? xff.substring(0, idx) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
