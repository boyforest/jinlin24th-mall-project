package com.jinlin24th.jinlin.common.auth;

import com.jinlin24th.jinlin.pojo.entity.CoffeeStore;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CoffeeAdminAccessService {
    private final AdminPermissionService permissions;

    public void requireAccess(Long adminId) {
        permissions.require(adminId, "coffee:menu:read");
    }

    public void requireStore(Long adminId, Long storeId, String permission) {
        permissions.requireStore(adminId, storeId, permission);
    }

    public void requireSuper(Long adminId) {
        permissions.requireSuper(adminId);
    }

    public List<CoffeeStore> stores(Long adminId) {
        return permissions.stores(adminId);
    }

    public List<String> permissions(Long adminId) {
        return permissions.permissions(adminId).stream()
                .filter(p -> p.startsWith("coffee:"))
                .toList();
    }
}
