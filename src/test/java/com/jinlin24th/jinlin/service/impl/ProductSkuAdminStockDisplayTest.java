package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinlin24th.jinlin.common.cache.ProductCacheService;
import com.jinlin24th.jinlin.mapper.ProductSkuMapper;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.service.SkuStockQueryService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * 管理端 SKU 列表的库存展示测试。
 * <p>
 * 管理端此前展示的是 {@code product_sku.stock} —— 一个**不驱动任何业务**的字段
 * （下单只扣 {@code inventory.stock}）。运营若照这个数字做补货决策就是被误导。
 * 展示必须改为 inventory 汇总，且无库存记录时显示 0（等于不可售），
 * 而不是回退到那个诱饵字段的旧值。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSkuAdminStockDisplayTest {

    private static final Long SKU_ID = 99L;

    @Mock
    private ProductCacheService productCacheService;
    @Mock
    private SkuStockQueryService inventoryService;
    @org.mockito.Mock
    private com.jinlin24th.jinlin.service.InventoryMaintenanceService inventoryMaintenanceService;
    @Mock
    private ProductSkuMapper productSkuMapper;

    private ProductSkuServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProductSku.class);
        service = new ProductSkuServiceImpl(productCacheService, inventoryService, inventoryMaintenanceService);
        ReflectionTestUtils.setField(service, "baseMapper", productSkuMapper);
    }

    private ProductSku skuWithStaleStock(int staleStock) {
        ProductSku sku = new ProductSku();
        sku.setId(SKU_ID);
        sku.setProductId(7L);
        sku.setSkuName("规格A");
        sku.setPrice(new BigDecimal("69.90"));
        sku.setStatus(1);
        // 这个值已不再随销售变化，展示时必须忽略
        sku.setStock(staleStock);
        return sku;
    }

    private void stubPage(ProductSku... skus) {
        Page<ProductSku> entityPage = new Page<>(1, 10, skus.length);
        entityPage.setRecords(List.of(skus));
        when(productSkuMapper.selectPage(any(), any())).thenReturn(entityPage);
    }

    @Test
    @DisplayName("管理端展示 inventory 汇总库存，而不是永不变化的 product_sku.stock")
    void shouldShowInventoryDerivedStock() {
        stubPage(skuWithStaleStock(200));
        when(inventoryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 37));

        IPage<ProductSkuVO> page = service.adminPage(1, 10, null, null, null);

        assertEquals(37, page.getRecords().get(0).getStock(),
            "运营必须看到真实可售库存，否则会在错误数字上做补货决策");
    }

    @Test
    @DisplayName("无 inventory 记录时显示 0（不可售），不回退到诱饵字段旧值")
    void shouldShowZeroWhenNoInventoryRecord() {
        stubPage(skuWithStaleStock(200));
        when(inventoryService.stockBySkuIds(anyCollection())).thenReturn(Map.of());

        IPage<ProductSkuVO> page = service.adminPage(1, 10, null, null, null);

        assertEquals(0, page.getRecords().get(0).getStock(),
            "没有库存记录等于不可售，绝不能回退到 product_sku.stock 的旧值");
    }

    @Test
    @DisplayName("多个 SKU 的库存必须各自对应，不能串号")
    void shouldMapStockPerSku() {
        ProductSku a = skuWithStaleStock(200);
        ProductSku b = skuWithStaleStock(300);
        b.setId(100L);
        stubPage(a, b);
        when(inventoryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 11, 100L, 22));

        IPage<ProductSkuVO> page = service.adminPage(1, 10, null, null, null);

        assertEquals(11, page.getRecords().get(0).getStock());
        assertEquals(22, page.getRecords().get(1).getStock());
    }

    @Test
    @DisplayName("分页元信息不能被库存回填改动")
    void shouldPreservePaginationMeta() {
        stubPage(skuWithStaleStock(1));
        when(inventoryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 5));

        IPage<ProductSkuVO> page = service.adminPage(2, 20, null, null, null);

        assertEquals(1, page.getCurrent(), "分页元信息来自实体分页，不应重建");
        assertEquals(10, page.getSize());
    }
}
