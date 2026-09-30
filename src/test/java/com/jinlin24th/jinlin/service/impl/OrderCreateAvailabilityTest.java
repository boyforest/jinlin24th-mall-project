package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.pojo.dto.OrderCreateDTO;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.InventoryLogService;
import com.jinlin24th.jinlin.service.InventoryService;
import com.jinlin24th.jinlin.service.OrderItemService;
import com.jinlin24th.jinlin.service.OrderMasterService;
import com.jinlin24th.jinlin.service.ProductService;
import com.jinlin24th.jinlin.service.ProductSkuService;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单时的商品可售性校验测试。
 * <p>
 * 修复的缺陷：`create()` 只校验 SKU/商品是否存在，**从不检查 status**。
 * 于是 `product.status=0`（已下架）或 `product_sku.status=0`（已停售）的商品
 * 仍可通过直接 POST skuId 下单——无论是缓存了旧链接的用户，还是刻意绕过前端的人。
 * <p>
 * 关键要求：必须在**占用库存之前**拒绝，否则下架商品也会扣减库存。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCreateAvailabilityTest {

    private static final Long SKU_ID = 99L;

    @Mock
    private OrderMasterService orderMasterService;
    @Mock
    private OrderItemService orderItemService;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductService productService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private InventoryLogService inventoryLogService;
    @Mock
    private AppUserService appUserService;
    @Mock
    private LambdaQueryChainWrapper<Inventory> inventoryQuery;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(Inventory.class);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMasterService", orderMasterService);
        ReflectionTestUtils.setField(service, "orderItemService", orderItemService);
        ReflectionTestUtils.setField(service, "productSkuService", productSkuService);
        ReflectionTestUtils.setField(service, "productService", productService);
        ReflectionTestUtils.setField(service, "inventoryService", inventoryService);
        ReflectionTestUtils.setField(service, "inventoryLogService", inventoryLogService);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "effectivePriceService", new com.jinlin24th.jinlin.service.EffectivePriceService());

        when(inventoryService.lambdaQuery()).thenReturn(inventoryQuery);
        when(inventoryQuery.eq(any(), any())).thenReturn(inventoryQuery);
        when(inventoryQuery.ge(any(), any())).thenReturn(inventoryQuery);
        when(inventoryQuery.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class)))
            .thenReturn(inventoryQuery);
        when(inventoryQuery.last(anyString())).thenReturn(inventoryQuery);
    }

    private static void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }

    private OrderCreateDTO dtoWithOneSku() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setReceiverName("张三");
        dto.setReceiverPhone("13800138000");
        dto.setReceiverAddress("测试地址");
        OrderCreateDTO.Item item = new OrderCreateDTO.Item();
        item.setSkuId(SKU_ID);
        item.setQuantity(1);
        dto.setItems(List.of(item));
        return dto;
    }

    private ProductSku skuWithStatus(int status) {
        ProductSku sku = new ProductSku();
        sku.setId(SKU_ID);
        sku.setProductId(1L);
        sku.setSkuName("规格A");
        sku.setPrice(new BigDecimal("69.90"));
        sku.setMemberPrice(new BigDecimal("59.90"));
        sku.setStatus(status);
        return sku;
    }

    private Product productWithStatus(int status) {
        Product product = new Product();
        product.setId(1L);
        product.setName("商品A");
        product.setStatus(status);
        return product;
    }

    @Test
    @DisplayName("已下架商品不得下单，且必须在占用库存之前拒绝")
    void offShelfProductMustBeRejected() {
        when(productSkuService.getById(SKU_ID)).thenReturn(skuWithStatus(1));
        when(productService.getById(1L)).thenReturn(productWithStatus(0)); // 已下架

        assertThrows(BizException.class,
            () -> service.create(1L, dtoWithOneSku()),
            "已下架商品必须被拒绝，不能仅凭 skuId 直接下单");

        // 关键：不能扣减库存
        verify(inventoryService, never()).lambdaUpdate();
    }

    @Test
    @DisplayName("已停售 SKU 不得下单，且必须在占用库存之前拒绝")
    void disabledSkuMustBeRejected() {
        when(productSkuService.getById(SKU_ID)).thenReturn(skuWithStatus(0)); // 已停售
        when(productService.getById(1L)).thenReturn(productWithStatus(1));

        assertThrows(BizException.class,
            () -> service.create(1L, dtoWithOneSku()),
            "已停售 SKU 必须被拒绝");

        verify(inventoryService, never()).lambdaUpdate();
    }

    @Test
    @DisplayName("在售商品 + 启用 SKU 应通过可售性校验（继续尝试占用库存）")
    void onSaleProductShouldPassAvailabilityCheck() {
        when(productSkuService.getById(SKU_ID)).thenReturn(skuWithStatus(1));
        when(productService.getById(1L)).thenReturn(productWithStatus(1));
        // 库存不足：用于证明请求已越过可售性校验、进入占用库存阶段
        when(inventoryQuery.one()).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.create(1L, dtoWithOneSku()));

        verify(inventoryService).lambdaQuery();
    }
}
