package com.jinlin24th.jinlin.common.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.service.SysAdminService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminPermissionService {
    private final SysAdminService admins;
    private final SysAdminCoffeeStoreMapper bindings;
    private final CoffeeStoreMapper stores;

    public SysAdmin enabled(Long id) {
        SysAdmin admin = id == null ? null : admins.getById(id);
        if (admin == null || !Integer.valueOf(1).equals(admin.getStatus())) {
            throw BizException.unauthorized("管理员账号已失效");
        }
        return admin;
    }

    public boolean superAdmin(Long id) {
        return admins.getRoleCodes(id).contains("SUPER_ADMIN");
    }

    public List<String> permissions(Long id) {
        return admins.getPermissionCodes(id);
    }

    public void require(Long id, String permission) {
        enabled(id);
        if (!permissions(id).contains(permission)) throw BizException.forbidden("无此模块权限");
    }

    public void requireSuper(Long id) {
        enabled(id);
        if (!superAdmin(id)) throw BizException.forbidden("仅超级管理员可操作");
    }

    public List<CoffeeStore> stores(Long id) {
        if (superAdmin(id))
            return stores.selectList(
                    Wrappers.<CoffeeStore>lambdaQuery().orderByAsc(CoffeeStore::getId));
        if (!admins.getRoleCodes(id).contains("COFFEE_ADMIN")) return List.of();
        List<Long> ids =
                bindings
                        .selectList(
                                Wrappers.<SysAdminCoffeeStore>lambdaQuery()
                                        .eq(SysAdminCoffeeStore::getAdminId, id))
                        .stream()
                        .map(SysAdminCoffeeStore::getStoreId)
                        .toList();
        return ids.isEmpty()
                ? List.of()
                : stores.selectList(
                        Wrappers.<CoffeeStore>lambdaQuery()
                                .in(CoffeeStore::getId, ids)
                                .orderByAsc(CoffeeStore::getId));
    }

    public void requireStore(Long id, Long storeId, String permission) {
        require(id, permission);
        if (stores(id).stream().noneMatch(s -> s.getId().equals(storeId)))
            throw BizException.of(BizCode.NOT_FOUND);
    }

    public void authorize(Long id, String method, String path) {
        enabled(id);
        if (Set.of("/admin/password", "/admin/logout", "/admin/auth/context").contains(path))
            return;
        if (path.startsWith("/admin/accounts")) {
            requireSuper(id);
            return;
        }
        String permission = permission(method, path);
        if (permission == null) {
            if (!superAdmin(id)) throw BizException.forbidden("此后台接口尚未授权");
        } else if (permission.equals("upload")) {
            if (permissions(id).stream()
                    .noneMatch(p -> p.endsWith(":manage") || p.endsWith(":write")))
                throw BizException.forbidden("无上传权限");
        } else require(id, permission);
    }

    /** 使用完整模块边界，避免 /admin/user 等直接请求绕过菜单。 */
    public static String permission(String method, String path) {
        if (path.equals("/admin/upload/image")) return "upload";
        if (path.startsWith("/admin/coffee/")) {
            if (path.endsWith("/refund") || path.endsWith("/reject")) return "coffee:orders:refund";
            if (path.startsWith("/admin/coffee/orders/") && !method.equals("GET"))
                return "coffee:orders:operate";
            if (path.startsWith("/admin/coffee/orders/") || path.equals("/admin/coffee/workbench"))
                return "coffee:orders:read";
            if (path.equals("/admin/coffee/ledger")) return "coffee:ledger:read";
            if (path.startsWith("/admin/coffee/store") && !method.equals("GET"))
                return "coffee:store:write";
            return method.equals("GET") ? "coffee:menu:read" : "coffee:menu:write";
        }
        String[] parts = path.split("/");
        if (parts.length < 3) return null;
        return switch (parts[2]) {
            case "product" -> "product:manage";
            case "order", "payment" -> "order:manage";
            case "inventory", "warehouse" -> "inventory:manage";
            case "user", "customer", "followRecord", "admins" -> "customer:manage";
            case "coupon" -> "coupon:manage";
            case "distribution" -> "distribution:manage";
            case "marketing" -> "marketing:manage";
            case "dashboard" -> "dashboard:view";
            default -> null;
        };
    }
}
