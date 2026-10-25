package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.vo.CoffeeCartVO;

import org.junit.jupiter.api.Test;

import java.util.List;

class CoffeeCartIsolationTest extends CoffeeDatabaseTestSupport {
    @Test
    void usersCannotReadUpdateSelectOrDeleteEachOthersCart() {
        CoffeeCartVO a = cart.add(10L, addDTO(2));
        CoffeeCartVO b = cart.add(20L, addDTO(3));
        assertEquals(1, cart.list(10L, Long.valueOf(storeId)).size());
        error(
                BizCode.COFFEE_CART_NOT_FOUND,
                () -> cart.update(10L, Long.valueOf(b.getId()), updateDTO(0, 9, null)));
        assertTrue(cart.delete(10L, Long.valueOf(b.getId())));
        assertEquals(3, cart.list(20L, Long.valueOf(storeId)).get(0).getQuantity());
        CoffeeCartSelectionDTO selection = new CoffeeCartSelectionDTO();
        selection.setStoreId(storeId);
        selection.setChecked(0);
        assertEquals(0, cart.selection(10L, selection).get(0).getChecked());
        assertEquals(1, cart.list(20L, Long.valueOf(storeId)).get(0).getChecked());
        assertTrue(cart.delete(10L, Long.valueOf(a.getId())));
        assertTrue(cart.delete(10L, Long.valueOf(a.getId())));
    }

    @Test
    void mergeAndEveryValidSingleUpdateInvalidateOlderVersions() {
        CoffeeCartVO row = cart.add(10L, addDTO(2));
        CoffeeCartAddDTO merge = addDTO(3);
        merge.setChecked(0);
        CoffeeCartVO merged = cart.add(10L, merge);
        assertEquals(row.getId(), merged.getId());
        assertEquals(5, merged.getQuantity());
        assertEquals(0, merged.getChecked());
        assertEquals(1, merged.getVersion());
        error(
                BizCode.COFFEE_CART_VERSION_CONFLICT,
                () -> cart.update(10L, Long.valueOf(row.getId()), updateDTO(0, 1, 1)));
        CoffeeCartVO unchanged = cart.update(10L, Long.valueOf(row.getId()), updateDTO(1, 5, null));
        assertEquals(2, unchanged.getVersion());
        CoffeeCartSelectionDTO selection = new CoffeeCartSelectionDTO();
        selection.setStoreId(storeId);
        selection.setChecked(1);
        assertEquals(3, cart.selection(10L, selection).get(0).getVersion());
        error(
                BizCode.COFFEE_CART_VERSION_CONFLICT,
                () -> cart.update(10L, Long.valueOf(row.getId()), updateDTO(2, 1, null)));
        CoffeeCartAddDTO overflow = addDTO(95);
        error(BizCode.PARAM_INVALID, () -> cart.add(10L, overflow));
        assertEquals(5, cart.list(10L, Long.valueOf(storeId)).get(0).getQuantity());
    }

    @Test
    void cartUsesCurrentPricesAndPreservesUnavailableRowsAndNames() {
        CoffeeCartVO row = cart.add(10L, addDTO(3));
        CoffeeProductSaveDTO dto = productDTO();
        dto.setName("改名拿铁");
        dto.getSkus().get(0).setPriceCent(2200L);
        catalog.saveProduct(ADMIN, null, Long.valueOf(product.getId()), dto);
        CoffeeCartVO current = cart.list(10L, Long.valueOf(storeId)).get(0);
        assertEquals("改名拿铁", current.getProductName());
        assertEquals(2200L, current.getUnitPriceCent());
        dto.setSkus(List.of(dto.getSkus().get(1)));
        catalog.saveProduct(ADMIN, null, Long.valueOf(product.getId()), dto);
        current = cart.list(10L, Long.valueOf(storeId)).get(0);
        assertFalse(current.getAvailable());
        assertEquals("SKU_DISABLED", current.getUnavailableReason());
        error(BizCode.COFFEE_SKU_UNAVAILABLE, () -> cart.add(10L, addDTO(1)));
        error(
                BizCode.COFFEE_SKU_UNAVAILABLE,
                () -> cart.update(10L, Long.valueOf(row.getId()), updateDTO(0, 4, null)));
        error(
                BizCode.COFFEE_SKU_UNAVAILABLE,
                () -> cart.update(10L, Long.valueOf(row.getId()), updateDTO(0, null, 1)));
        CoffeeCartSelectionDTO selection = new CoffeeCartSelectionDTO();
        selection.setStoreId(storeId);
        selection.setChecked(0);
        current = cart.selection(10L, selection).get(0);
        assertEquals(1, current.getChecked());
        assertEquals(0, current.getVersion());
        current = cart.update(10L, Long.valueOf(row.getId()), updateDTO(0, 2, 0));
        assertEquals(2, current.getQuantity());
        assertEquals(0, current.getChecked());
        assertTrue(cart.delete(10L, Long.valueOf(row.getId())));
    }

    @Test
    void reasonPriorityAndDisabledCatalogStillAllowRemoval() {
        CoffeeCartVO row = cart.add(10L, addDTO(1));
        jdbc.update("UPDATE coffee_sku SET available=FALSE, status=0");
        jdbc.update("UPDATE coffee_product SET status=0");
        jdbc.update("UPDATE coffee_category SET status=0");
        jdbc.update("UPDATE coffee_store SET status=0");
        assertEquals(
                "STORE_DISABLED",
                cart.list(10L, Long.valueOf(storeId)).get(0).getUnavailableReason());
        jdbc.update("UPDATE coffee_store SET status=1");
        assertEquals(
                "CATEGORY_DISABLED",
                cart.list(10L, Long.valueOf(storeId)).get(0).getUnavailableReason());
        jdbc.update("UPDATE coffee_category SET status=1");
        assertEquals(
                "PRODUCT_OFF_SHELF",
                cart.list(10L, Long.valueOf(storeId)).get(0).getUnavailableReason());
        jdbc.update("UPDATE coffee_product SET status=1");
        assertEquals(
                "SKU_DISABLED",
                cart.list(10L, Long.valueOf(storeId)).get(0).getUnavailableReason());
        jdbc.update("UPDATE coffee_sku SET status=1");
        assertEquals(
                "SOLD_OUT", cart.list(10L, Long.valueOf(storeId)).get(0).getUnavailableReason());
        assertTrue(cart.delete(10L, Long.valueOf(row.getId())));
    }

    @Test
    void disabledCatalogCanStillReadAndRemovePersonalCart() {
        CoffeeCartVO row = cart.add(10L, addDTO(1));
        com.jinlin24th.jinlin.service.CoffeeCatalogSupport support =
                org.springframework.test.util.AopTestUtils.getTargetObject(catalogSupport);
        org.springframework.test.util.ReflectionTestUtils.setField(support, "enabled", false);
        try {
            error(BizCode.COFFEE_STORE_NOT_FOUND, () -> catalog.currentStore());
            assertEquals(
                    "STORE_DISABLED",
                    cart.list(10L, Long.valueOf(storeId)).get(0).getUnavailableReason());
            error(BizCode.COFFEE_SKU_UNAVAILABLE, () -> cart.add(10L, addDTO(1)));
            assertTrue(cart.delete(10L, Long.valueOf(row.getId())));
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(support, "enabled", true);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.jinlin24th.jinlin.service.CoffeeCatalogSupport catalogSupport;

    @Test
    void unknownOrOtherStoreSkuAndGuestAreRejected() {
        CoffeeCartAddDTO missing = addDTO(1);
        missing.setSkuId("999999");
        error(BizCode.COFFEE_SKU_NOT_FOUND, () -> cart.add(10L, missing));
        missing.setStoreId("999999");
        error(BizCode.COFFEE_STORE_NOT_FOUND, () -> cart.add(10L, missing));
        CoffeeCartAddDTO soldOut = addDTO(1);
        soldOut.setSkuId(product.getSkus().get(1).getId());
        error(BizCode.COFFEE_SKU_UNAVAILABLE, () -> cart.add(10L, soldOut));
        error(BizCode.UNAUTHORIZED, () -> cart.list(null, Long.valueOf(storeId)));
    }
}
