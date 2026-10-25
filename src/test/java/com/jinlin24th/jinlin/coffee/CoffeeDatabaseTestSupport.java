package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.vo.*;
import com.jinlin24th.jinlin.service.*;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

@SpringJUnitConfig(CoffeeTestConfig.class)
@TestPropertySource(
        properties = {
            "app.coffee.catalog-enabled=true",
            "app.business-order.goods-store-name=商品商城"
        })
abstract class CoffeeDatabaseTestSupport {
    @Autowired protected CoffeeCatalogService catalog;
    @Autowired protected CoffeeCartAppService cart;
    @Autowired protected BusinessOrderQueryService orders;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected SysAdminService admins;
    protected static final Long ADMIN = 1L;
    protected String storeId;
    protected String categoryId;
    protected CoffeeAdminProductVO product;

    @BeforeEach
    void resetDatabase() {
        for (String table :
                List.of(
                        "coffee_cart",
                        "coffee_sku",
                        "coffee_product",
                        "coffee_category",
                        "coffee_store",
                        "order_item",
                        "payment_record",
                        "order_master")) {
            jdbc.update("DELETE FROM " + table);
        }
        reset(admins);
        when(admins.getById(anyLong())).thenAnswer(invocation -> {
            var admin = new com.jinlin24th.jinlin.pojo.entity.SysAdmin();
            admin.setId(invocation.getArgument(0));
            admin.setUsername(ADMIN.equals(admin.getId()) ? "admin" : "staff");
            admin.setStatus(1);
            return admin;
        });
        when(admins.getPermissionCodes(ADMIN)).thenReturn(
                com.jinlin24th.jinlin.common.auth.AdminPermissions.ROLES.get("SUPER_ADMIN"));
        when(admins.getRoleCodes(ADMIN)).thenReturn(List.of("SUPER_ADMIN"));
        storeId = catalog.saveStore(ADMIN, null, storeDTO()).getId();
        CoffeeCategorySaveDTO category = new CoffeeCategorySaveDTO();
        category.setName("咖啡");
        category.setSort(0);
        category.setStatus(1);
        categoryId = catalog.saveCategory(ADMIN, Long.valueOf(storeId), null, category).getId();
        product = catalog.saveProduct(ADMIN, Long.valueOf(storeId), null, productDTO());
    }

    protected CoffeeStoreSaveDTO storeDTO() {
        CoffeeStoreSaveDTO dto = new CoffeeStoreSaveDTO();
        dto.setName("仅测试门店");
        dto.setAddress("测试地址");
        dto.setOpeningHours("09:00–18:00");
        dto.setBusinessStatus("OPEN");
        return dto;
    }

    protected CoffeeProductSaveDTO productDTO() {
        CoffeeProductSaveDTO dto = new CoffeeProductSaveDTO();
        dto.setCategoryId(categoryId);
        dto.setName("拿铁");
        dto.setDescription("测试描述");
        dto.setSort(0);
        dto.setStatus(1);
        dto.setSkus(List.of(skuDTO("HOT", "REGULAR", true), skuDTO("ICED", "NONE", false)));
        return dto;
    }

    protected CoffeeSkuSaveDTO skuDTO(String temperature, String sugar, boolean available) {
        CoffeeSkuSaveDTO dto = new CoffeeSkuSaveDTO();
        dto.setName(temperature + sugar);
        dto.setTemperature(temperature);
        dto.setSugar(sugar);
        dto.setPriceCent(1800L);
        dto.setStatus(1);
        dto.setAvailable(available);
        return dto;
    }

    protected CoffeeCartAddDTO addDTO(int quantity) {
        CoffeeCartAddDTO dto = new CoffeeCartAddDTO();
        dto.setStoreId(storeId);
        dto.setSkuId(product.getSkus().get(0).getId());
        dto.setQuantity(quantity);
        return dto;
    }

    protected CoffeeCartUpdateDTO updateDTO(long version, Integer quantity, Integer checked) {
        CoffeeCartUpdateDTO dto = new CoffeeCartUpdateDTO();
        dto.setExpectedVersion(version);
        dto.setQuantity(quantity);
        dto.setChecked(checked);
        return dto;
    }

    protected void error(BizCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(BizException.class, action).getBizCode());
    }
}
