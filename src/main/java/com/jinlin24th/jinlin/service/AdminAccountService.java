package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinlin24th.jinlin.common.auth.*;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.pojo.vo.*;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class AdminAccountService {
    private final SysAdminMapper admins;
    private final SysRoleMapper roles;
    private final SysAdminRoleMapper adminRoles;
    private final SysAdminCoffeeStoreMapper bindings;
    private final SysOperationLogMapper logs;
    private final SysAdminService auth;
    private final AdminPermissionService permissions;
    private final AuthSessionService sessions;
    private final PasswordEncoder encoder;

    public AdminContextVO context(Long actor) {
        SysAdmin a = permissions.enabled(actor);
        AdminContextVO v = new AdminContextVO();
        v.setAdminId(a.getId().toString());
        v.setUsername(a.getUsername());
        v.setRealName(a.getRealName());
        v.setRoles(auth.getRoleCodes(actor));
        v.setPermissions(permissions.permissions(actor));
        v.setStores(stores(actor, false));
        List<String> p = v.getPermissions();
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("dashboard:view", "/dashboard");
        routes.put("coffee:orders:read", "/coffee/workbench");
        routes.put("coffee:menu:read", "/coffee/menu");
        routes.put("product:manage", "/products");
        routes.put("order:manage", "/orders");
        routes.put("inventory:manage", "/inventory");
        routes.put("customer:manage", "/customers");
        routes.put("coupon:manage", "/coupons");
        routes.put("distribution:manage", "/distribution");
        routes.put("marketing:manage", "/marketing-activities");
        routes.put("admin:accounts:manage", "/system/accounts");
        v.setDefaultRoute(
                routes.entrySet().stream()
                        .filter(e -> p.contains(e.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null));
        return v;
    }

    public List<CoffeeAdminContextVO.Store> stores(Long actor, boolean superOnly) {
        if (superOnly) permissions.requireSuper(actor);
        return permissions.stores(actor).stream()
                .map(
                        s -> {
                            var v = new CoffeeAdminContextVO.Store();
                            v.setId(s.getId().toString());
                            v.setName(s.getName());
                            return v;
                        })
                .toList();
    }

    public List<Map<String, Object>> roleOptions(Long actor) {
        permissions.requireSuper(actor);
        return List.of("SUPER_ADMIN", "GOODS_ADMIN", "COFFEE_ADMIN").stream()
                .map(
                        code -> {
                            SysRole role =
                                    roles.selectOne(
                                            Wrappers.<SysRole>lambdaQuery()
                                                    .eq(SysRole::getRoleCode, code));
                            return Map.<String, Object>of(
                                    "code",
                                    code,
                                    "name",
                                    role.getRoleName(),
                                    "description",
                                    Objects.toString(role.getDescription(), ""),
                                    "permissions",
                                    AdminPermissions.ROLES.get(code));
                        })
                .toList();
    }

    public IPage<AdminAccountVO> list(
            Long actor, int page, int size, String keyword, String roleCode, Integer status) {
        permissions.requireSuper(actor);
        page(page, size);
        var q = Wrappers.<SysAdmin>lambdaQuery();
        if (keyword != null && !keyword.isBlank())
            q.and(
                    w ->
                            w.like(SysAdmin::getUsername, keyword.trim())
                                    .or()
                                    .like(SysAdmin::getRealName, keyword.trim()));
        if (status != null) {
            if (status != 0 && status != 1) throw BizException.badRequest("status 必须为0或1");
            q.eq(SysAdmin::getStatus, status);
        }
        if (roleCode != null) {
            SysRole role =
                    roles.selectOne(
                            Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
            List<Long> ids =
                    role == null
                            ? List.of()
                            : adminRoles
                                    .selectList(
                                            Wrappers.<SysAdminRole>lambdaQuery()
                                                    .eq(SysAdminRole::getRoleId, role.getId()))
                                    .stream()
                                    .map(SysAdminRole::getAdminId)
                                    .toList();
            if (ids.isEmpty()) q.eq(SysAdmin::getId, -1L);
            else q.in(SysAdmin::getId, ids);
        }
        return admins.selectPage(new Page<>(page, size), q.orderByDesc(SysAdmin::getId))
                .convert(this::view);
    }

    public static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw BizException.badRequest("分页参数无效");
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public AdminAccountVO create(Long actor, AdminAccountSaveDTO dto) {
        permissions.requireSuper(actor);
        validateAccess(actor, dto.getRoleCode(), dto.getStoreId());
        SysAdmin a = new SysAdmin();
        a.setUsername(dto.getUsername());
        a.setRealName(dto.getRealName());
        a.setPasswordHash(encodeTemporary(dto.getTemporaryPassword()));
        a.setStatus(1);
        a.setDeleted(0);
        a.setMustChangePwd(1);
        try {
            admins.insert(a);
        } catch (DuplicateKeyException e) {
            throw BizException.of(BizCode.ADMIN_ACCOUNT_CONFLICT);
        }
        assign(a.getId(), dto.getRoleCode(), dto.getStoreId());
        audit(actor, a.getId(), "CREATE");
        return view(a);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public AdminAccountVO profile(Long actor, Long id, AdminAccountProfileDTO dto) {
        SysAdmin a = target(actor, id);
        admins.update(
                null,
                Wrappers.<SysAdmin>lambdaUpdate()
                        .eq(SysAdmin::getId, id)
                        .set(SysAdmin::getRealName, dto.getRealName()));
        audit(actor, id, "PROFILE");
        return view(admins.selectById(id));
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public AdminAccountVO access(Long actor, Long id, AdminAccountAccessDTO dto) {
        SysAdmin a = target(actor, id);
        validateAccess(actor, dto.getRoleCode(), dto.getStoreId());
        protectLast(a, !"SUPER_ADMIN".equals(dto.getRoleCode()));
        sessions.onLogoutAdmin(a.getUsername());
        assign(id, dto.getRoleCode(), dto.getStoreId());
        audit(actor, id, "ACCESS");
        return view(a);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public AdminAccountVO status(Long actor, Long id, int status) {
        SysAdmin a = target(actor, id);
        protectLast(a, status == 0);
        if (status == 0) sessions.onLogoutAdmin(a.getUsername());
        a.setStatus(status);
        admins.updateById(a);
        audit(actor, id, "STATUS_" + status);
        return view(a);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public boolean reset(Long actor, Long id, String password) {
        SysAdmin a = target(actor, id);
        sessions.onLogoutAdmin(a.getUsername());
        a.setPasswordHash(encodeTemporary(password));
        a.setMustChangePwd(1);
        admins.updateById(a);
        audit(actor, id, "RESET_PASSWORD");
        return true;
    }

    private String encodeTemporary(String password) {
        if (password == null
                || password.length() < 8
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw BizException.badRequest("临时密码至少8字符且最多72 UTF-8字节");
        return encoder.encode(password);
    }

    private SysAdmin target(Long actor, Long id) {
        permissions.requireSuper(actor);
        SysAdmin a = admins.lockById(id);
        if (a == null) throw BizException.of(BizCode.NOT_FOUND);
        return a;
    }

    private void protectLast(SysAdmin a, boolean removing) {
        roles.lockSuperRole(); // 全局超管资格变更互斥；READ_COMMITTED 计数读取上次提交。
        if (!removing
                || a.getStatus() != 1
                || !auth.getRoleCodes(a.getId()).contains("SUPER_ADMIN")) return;
        SysRole superRole =
                roles.selectOne(
                        Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, "SUPER_ADMIN"));
        List<Long> ids =
                adminRoles
                        .selectList(
                                Wrappers.<SysAdminRole>lambdaQuery()
                                        .eq(SysAdminRole::getRoleId, superRole.getId()))
                        .stream()
                        .map(SysAdminRole::getAdminId)
                        .toList();
        if (admins.selectCount(
                        Wrappers.<SysAdmin>lambdaQuery()
                                .in(SysAdmin::getId, ids)
                                .eq(SysAdmin::getStatus, 1))
                <= 1) throw BizException.of(BizCode.ADMIN_LAST_SUPER);
    }

    private void validateAccess(Long actor, String role, String store) {
        if (!AdminPermissions.ROLES.containsKey(role)) throw BizException.badRequest("角色不可分配");
        SysRole r =
                roles.selectOne(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getRoleCode, role)
                                .eq(SysRole::getStatus, 1));
        if (r == null) throw BizException.of(BizCode.ADMIN_ACCOUNT_CONFLICT, "角色已停用");
        if ("COFFEE_ADMIN".equals(role)) {
            Long sid = CoffeeValues.id(store);
            if (permissions.stores(actor).stream().noneMatch(s -> s.getId().equals(sid)))
                throw BizException.of(BizCode.NOT_FOUND);
        } else if (store != null) throw BizException.badRequest("此角色不绑定咖啡门店");
    }

    private void assign(Long id, String code, String store) {
        roles.lockSuperRole();
        adminRoles.delete(Wrappers.<SysAdminRole>lambdaQuery().eq(SysAdminRole::getAdminId, id));
        bindings.delete(
                Wrappers.<SysAdminCoffeeStore>lambdaQuery()
                        .eq(SysAdminCoffeeStore::getAdminId, id));
        SysRole role =
                roles.selectOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, code));
        SysAdminRole link = new SysAdminRole();
        link.setAdminId(id);
        link.setRoleId(role.getId());
        adminRoles.insert(link);
        if (store != null) {
            SysAdminCoffeeStore bind = new SysAdminCoffeeStore();
            bind.setAdminId(id);
            bind.setStoreId(CoffeeValues.id(store));
            bindings.insert(bind);
        }
    }

    private AdminAccountVO view(SysAdmin a) {
        var v = new AdminAccountVO();
        v.setId(a.getId().toString());
        v.setUsername(a.getUsername());
        v.setRealName(a.getRealName());
        v.setRoles(assignedRoles(a.getId()));
        v.setStatus(a.getStatus());
        v.setMustChangePwd(a.getMustChangePwd() == 1);
        v.setLastLoginTime(
                a.getLastLoginTime() == null
                        ? null
                        : a.getLastLoginTime().atOffset(ZoneOffset.ofHours(8)).toString());
        var s = permissions.stores(a.getId());
        if (!permissions.superAdmin(a.getId()) && !s.isEmpty()) {
            v.setStoreId(s.get(0).getId().toString());
            v.setStoreName(s.get(0).getName());
        }
        return v;
    }

    private List<String> assignedRoles(Long id) {
        List<Long> ids =
                adminRoles
                        .selectList(
                                Wrappers.<SysAdminRole>lambdaQuery()
                                        .eq(SysAdminRole::getAdminId, id))
                        .stream()
                        .map(SysAdminRole::getRoleId)
                        .toList();
        return ids.isEmpty()
                ? List.of()
                : roles
                        .selectList(
                                Wrappers.<SysRole>lambdaQuery()
                                        .in(SysRole::getId, ids)
                                        .orderByAsc(SysRole::getSort))
                        .stream()
                        .map(SysRole::getRoleCode)
                        .toList();
    }

    private void audit(Long actor, Long target, String action) {
        SysOperationLog log = new SysOperationLog();
        log.setAdminId(actor);
        log.setUsername(admins.selectById(actor).getUsername());
        log.setModule("admin:accounts");
        log.setOperation(action);
        log.setRequestParams("{\"targetId\":\"" + target + "\"}");
        log.setStatus(1);
        logs.insert(log);
    }
}
