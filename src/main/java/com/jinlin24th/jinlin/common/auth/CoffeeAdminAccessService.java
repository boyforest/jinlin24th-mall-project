package com.jinlin24th.jinlin.common.auth;

import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.service.SysAdminService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/** 阶段 A 仅向启用 SUPER_ADMIN 角色的平台管理员开放。 */
@Service
@RequiredArgsConstructor
public class CoffeeAdminAccessService {
    private final SysAdminService sysAdminService;

    public void requireAccess(Long adminId) {
        if (adminId == null || !sysAdminService.getRoleCodes(adminId).contains("SUPER_ADMIN")) {
            throw BizException.forbidden("仅平台超级管理员可维护咖啡菜单");
        }
    }
}
