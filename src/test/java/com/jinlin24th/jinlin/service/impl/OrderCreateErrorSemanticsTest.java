package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.jinlin24th.jinlin.common.constant.BizCode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 下单失败时的错误语义测试。
 * <p>
 * 修复的两个缺陷：
 * <ol>
 *   <li><b>库存不足返回 500</b>：原实现抛 {@code IllegalArgumentException}，
 *       被全局异常处理器兜成"系统繁忙"（HTTP 500）。用户明明只是遇到售罄，
 *       却看到系统错误，前端也无法给出"库存不足"的提示。</li>
 *   <li><b>明细被静默丢弃</b>：无效明细返回 {@code null} 后被
 *       {@code filter(Objects::nonNull)} 过滤掉——购物车 3 件商品有 1 件失效时，
 *       会生成一张只含 2 件的订单且不报错，用户收到的货比预期少、金额却按 2 件算。</li>
 * </ol>
 * 正确语义：任何一条明细不可下单都必须**明确报错**，绝不生成"缩水订单"。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCreateErrorSemanticsTest {

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
        // 定价规则已收敛到 EffectivePriceService（本用例不关注价格，用真实实现即可）
        ReflectionTestUtils.setField(service, "effectivePriceService", new com.jinlin24th.jinlin.service.EffectivePriceService());
        // 买家查询默认返回 null（等价于非会员），价格按售价
        when(appUserService.getById(any())).thenReturn(null);

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

    private OrderCreateDTO dtoWith(Long skuId, Integer quantity) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setReceiverName("张三");
        dto.setReceiverPhone("13800138000");
        dto.setReceiverAddress("测试地址");
        OrderCreateDTO.Item item = new OrderCreateDTO.Item();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        dto.setItems(List.of(item));
        return dto;
    }

    private ProductSku enabledSku() {
        ProductSku sku = new ProductSku();
        sku.setId(SKU_ID);
        sku.setProductId(1L);
        sku.setSkuName("规格A");
        sku.setPrice(new BigDecimal("69.90"));
        sku.setStatus(1);
        return sku;
    }

    private Product enabledProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setName("商品A");
        product.setStatus(1);
        return product;
    }

    @Test
    @DisplayName("库存不足必须返回业务错误（400），不能是 IllegalArgumentException/500")
    void outOfStockMustBeBusinessError() {
        when(productSkuService.getById(SKU_ID)).thenReturn(enabledSku());
        when(productService.getById(1L)).thenReturn(enabledProduct());
        when(inventoryQuery.one()).thenReturn(null); // 无可用库存

        Exception ex = assertThrows(Exception.class, () -> service.create(1L, dtoWith(SKU_ID, 1)));

        BizException biz = assertInstanceOf(BizException.class, ex,
            "库存不足是可预期的业务情况，必须是 BizException（否则前端只能看到 500 系统繁忙）");
        assertEquals(BizCode.BAD_REQUEST.getCode(), biz.getCode());
        assertTrue(biz.getMessage().contains("库存"),
            "错误信息应说明是库存问题，便于前端直接展示：" + biz.getMessage());
    }

    @Test
    @DisplayName("SKU 不存在时不得静默丢弃明细（会生成缩水订单）")
    void missingSkuMustNotBeSilentlyDropped() {
        when(productSkuService.getById(SKU_ID)).thenReturn(null);

        assertThrows(BizException.class, () -> service.create(1L, dtoWith(SKU_ID, 1)),
            "不存在的 SKU 必须明确报错，不能悄悄少发一件");
    }

    @Test
    @DisplayName("商品不存在时不得静默丢弃明细")
    void missingProductMustNotBeSilentlyDropped() {
        when(productSkuService.getById(SKU_ID)).thenReturn(enabledSku());
        when(productService.getById(1L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.create(1L, dtoWith(SKU_ID, 1)));
    }

    @Test
    @DisplayName("数量非法（0 或负数）时必须报错，不能静默忽略该明细")
    void invalidQuantityMustBeRejected() {
        assertThrows(BizException.class, () -> service.create(1L, dtoWith(SKU_ID, 0)));
        assertThrows(BizException.class, () -> service.create(1L, dtoWith(SKU_ID, -3)));
    }

    @Test
    @DisplayName("skuId 为空时必须报错")
    void nullSkuIdMustBeRejected() {
        assertThrows(BizException.class, () -> service.create(1L, dtoWith(null, 1)));
    }

    @Test
    @DisplayName("多件商品中有一件失效时，整单必须失败而不是只下能下的那几件")
    void partialInvalidCartMustFailWholeOrder() {
        when(productSkuService.getById(99L)).thenReturn(enabledSku());
        when(productSkuService.getById(100L)).thenReturn(null); // 第二件已失效

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setReceiverName("张三");
        dto.setReceiverPhone("13800138000");
        dto.setReceiverAddress("测试地址");
        OrderCreateDTO.Item ok = new OrderCreateDTO.Item();
        ok.setSkuId(99L);
        ok.setQuantity(1);
        OrderCreateDTO.Item invalid = new OrderCreateDTO.Item();
        invalid.setSkuId(100L);
        invalid.setQuantity(1);
        dto.setItems(List.of(ok, invalid));

        assertThrows(BizException.class, () -> service.create(1L, dto),
            "部分明细失效时必须整单失败，否则用户收到的货与预期不符");
    }
}
