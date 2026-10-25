package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jinlin24th.jinlin.common.constant.BizCode;

import org.junit.jupiter.api.Test;

import java.util.List;

class CoffeeAdminAccessTest extends CoffeeDatabaseTestSupport {
    @Test
    void everyManagementEntryChecksEnabledSuperAdminRole() {
        when(admins.getRoleCodes(20L)).thenReturn(List.of("COFFEE_STAFF"));
        error(BizCode.FORBIDDEN, () -> catalog.context(20L));
        error(BizCode.FORBIDDEN, () -> catalog.adminStore(20L, Long.valueOf(storeId)));
        error(BizCode.FORBIDDEN, () -> catalog.saveStore(20L, null, storeDTO()));
        error(BizCode.FORBIDDEN, () -> catalog.saveStore(20L, Long.valueOf(storeId), storeDTO()));
        error(BizCode.FORBIDDEN, () -> catalog.adminMenu(20L, Long.valueOf(storeId)));
        error(
                BizCode.FORBIDDEN,
                () -> catalog.saveCategory(20L, Long.valueOf(storeId), null, null));
        error(
                BizCode.FORBIDDEN,
                () -> catalog.saveProduct(20L, Long.valueOf(storeId), null, productDTO()));
        error(
                BizCode.FORBIDDEN,
                () -> catalog.saveProduct(20L, null, Long.valueOf(product.getId()), productDTO()));
        when(admins.getRoleCodes(ADMIN)).thenReturn(List.of());
        error(BizCode.FORBIDDEN, () -> catalog.context(ADMIN));
    }

    @Test
    void onlyOneStoreCanExistAndContextHasMinimalPermissions() {
        assertEquals(
                List.of("coffee:menu:read", "coffee:menu:write"),
                catalog.context(ADMIN).getPermissions());
        assertEquals(storeId, catalog.context(ADMIN).getStores().get(0).getId());
        error(
                BizCode.COFFEE_STORE_ALREADY_EXISTS,
                () -> catalog.saveStore(ADMIN, null, storeDTO()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM coffee_store", Integer.class));
    }
}
