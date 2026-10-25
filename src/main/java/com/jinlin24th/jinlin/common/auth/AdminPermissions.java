package com.jinlin24th.jinlin.common.auth;

import java.util.*;

/** 本轮固定业务角色；旧角色继续读取其原有数据库授权。 */
public final class AdminPermissions {
    private AdminPermissions() {}

    public static final List<String> GOODS =
            List.of(
                    "dashboard:view",
                    "product:manage",
                    "order:manage",
                    "inventory:manage",
                    "customer:manage",
                    "coupon:manage",
                    "distribution:manage");
    public static final List<String> COFFEE =
            List.of(
                    "coffee:menu:read",
                    "coffee:menu:write",
                    "coffee:store:write",
                    "coffee:orders:read",
                    "coffee:orders:operate",
                    "coffee:orders:refund",
                    "coffee:ledger:read");
    public static final Map<String, List<String>> ROLES;

    static {
        List<String> all = new ArrayList<>(GOODS);
        all.addAll(List.of("admin:accounts:manage", "marketing:manage"));
        all.addAll(COFFEE);
        ROLES =
                Map.of(
                        "SUPER_ADMIN",
                        List.copyOf(all),
                        "GOODS_ADMIN",
                        GOODS,
                        "COFFEE_ADMIN",
                        COFFEE);
    }
}
