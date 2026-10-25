package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.vo.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class CoffeeCatalogAvailabilityTest extends CoffeeDatabaseTestSupport {
    @Test
    void realMenuShowsSoldOutSkusAndAlwaysDisablesOrdering() {
        CoffeeStoreVO store = catalog.currentStore();
        assertTrue(store.getCapabilities().getBrowse());
        assertTrue(store.getCapabilities().getAddToCart());
        assertFalse(store.getCapabilities().getPlaceOrder());
        assertFalse(store.getCapabilities().getPayOnline());
        assertEquals("ORDERING_DISABLED", store.getOrderUnavailableReason());
        CoffeeMenuVO menu = catalog.menu(Long.valueOf(storeId));
        assertEquals(2, menu.getProducts().get(0).getSkus().size());
        assertFalse(menu.getProducts().get(0).getSkus().get(1).getAvailable());
        assertEquals("SOLD_OUT", menu.getProducts().get(0).getSkus().get(1).getUnavailableReason());
        CoffeeStoreSaveDTO closed = storeDTO();
        closed.setBusinessStatus("CLOSED");
        store = catalog.saveStore(ADMIN, Long.valueOf(storeId), closed);
        assertTrue(store.getCapabilities().getAddToCart());
        assertFalse(store.getCapabilities().getPlaceOrder());
        assertEquals("STORE_CLOSED", store.getOrderUnavailableReason());
        assertNotNull(cart.add(10L, addDTO(1)));
    }

    @Test
    void publicAndAdminFiltersDifferAndVersionTracksEveryChange() {
        String before = catalog.menu(Long.valueOf(storeId)).getVersion();
        CoffeeProductSaveDTO dto = productDTO();
        dto.setStatus(0);
        catalog.saveProduct(ADMIN, null, Long.valueOf(product.getId()), dto);
        assertTrue(catalog.menu(Long.valueOf(storeId)).getProducts().isEmpty());
        assertEquals(1, catalog.adminMenu(ADMIN, Long.valueOf(storeId)).getProducts().size());
        assertNotEquals(before, catalog.menu(Long.valueOf(storeId)).getVersion());
        CoffeeCategorySaveDTO category = new CoffeeCategorySaveDTO();
        category.setName("停用分类");
        category.setSort(0);
        category.setStatus(0);
        catalog.saveCategory(ADMIN, null, Long.valueOf(categoryId), category);
        assertTrue(catalog.menu(Long.valueOf(storeId)).getCategories().isEmpty());
        assertEquals(1, catalog.adminMenu(ADMIN, Long.valueOf(storeId)).getCategories().size());
        error(
                BizCode.PARAM_INVALID,
                () ->
                        catalog.saveProduct(
                                ADMIN, null, Long.valueOf(product.getId()), productDTO()));
    }

    @Test
    void omittedSkusAreDisabledAndReactivatedWithStableIds() {
        String first = product.getSkus().get(0).getId();
        String second = product.getSkus().get(1).getId();
        CoffeeProductSaveDTO dto = productDTO();
        dto.setSkus(List.of(dto.getSkus().get(0)));
        CoffeeAdminProductVO saved =
                catalog.saveProduct(ADMIN, null, Long.valueOf(product.getId()), dto);
        assertEquals(first, saved.getSkus().get(0).getId());
        assertEquals(0, saved.getSkus().get(1).getStatus());
        assertEquals(2, saved.getSkus().size());
        saved = catalog.saveProduct(ADMIN, null, Long.valueOf(product.getId()), productDTO());
        assertEquals(second, saved.getSkus().get(1).getId());
        assertEquals(1, saved.getSkus().get(1).getStatus());
    }

    @Test
    void invalidCombinationOwnershipAndActiveCategoryDisableAreRejected() {
        CoffeeCategorySaveDTO category = new CoffeeCategorySaveDTO();
        category.setName("分类");
        category.setSort(0);
        category.setStatus(0);
        error(
                BizCode.PARAM_INVALID,
                () -> catalog.saveCategory(ADMIN, null, Long.valueOf(categoryId), category));
        CoffeeProductSaveDTO duplicate = productDTO();
        duplicate.setSkus(List.of(duplicate.getSkus().get(0), duplicate.getSkus().get(0)));
        error(
                BizCode.PARAM_INVALID,
                () -> catalog.saveProduct(ADMIN, null, Long.valueOf(product.getId()), duplicate));
        CoffeeProductSaveDTO foreign = productDTO();
        foreign.getSkus().get(0).setId("9999999");
        error(
                BizCode.PARAM_INVALID,
                () -> catalog.saveProduct(ADMIN, null, Long.valueOf(product.getId()), foreign));
        CoffeeProductSaveDTO rewrite = productDTO();
        rewrite.getSkus().get(0).setId(product.getSkus().get(1).getId());
        error(
                BizCode.PARAM_INVALID,
                () -> catalog.saveProduct(ADMIN, null, Long.valueOf(product.getId()), rewrite));
    }

    @Test
    void retainedSkuCanChangeToUnusedCombinationWithoutChangingId() {
        CoffeeProductSaveDTO dto = productDTO();
        dto.getSkus().get(0).setId(product.getSkus().get(0).getId());
        dto.getSkus().get(0).setSugar("LESS");
        CoffeeAdminProductVO saved =
                catalog.saveProduct(ADMIN, null, Long.valueOf(product.getId()), dto);
        assertEquals(product.getSkus().get(0).getId(), saved.getSkus().get(0).getId());
        assertEquals("LESS", saved.getSkus().get(0).getSugar());
    }

    @Test
    void databaseFailureRollsBackProductAndSkuChanges() {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM coffee_product", Long.class);
        CoffeeProductSaveDTO invalid = productDTO();
        invalid.getSkus().get(1).setPriceCent(0L);
        assertThrows(
                RuntimeException.class,
                () -> catalog.saveProduct(ADMIN, Long.valueOf(storeId), null, invalid));
        assertEquals(
                before, jdbc.queryForObject("SELECT COUNT(*) FROM coffee_product", Long.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM coffee_sku", Integer.class));
    }

    @Test
    void missingDisabledStoreNeverCreatesDemoData() {
        jdbc.update("UPDATE coffee_store SET status=0");
        error(BizCode.COFFEE_STORE_NOT_FOUND, () -> catalog.currentStore());
        error(BizCode.COFFEE_STORE_NOT_FOUND, () -> catalog.menu(Long.valueOf(storeId)));
        assertFalse(catalog.adminStore(ADMIN, Long.valueOf(storeId)).getCapabilities().getBrowse());
    }
}
