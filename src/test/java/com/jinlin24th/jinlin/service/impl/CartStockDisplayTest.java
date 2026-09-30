package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.jinlin24th.jinlin.pojo.entity.Cart;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.vo.CartVO;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.CartService;
import com.jinlin24th.jinlin.service.SkuStockQueryService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * 购物车库存展示测试。
 * <p>
 * 购物车此前用 {@code product_sku.stock} 作为"最多可加购数量"和展示值，
 * 而那个字段从不随销售变化——用户卖光了仍能加购，直到下单才报"库存不足"。
 * 必须改为 inventory 汇总（实时），保证购物车看到的就是能否下单的依据。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartStockDisplayTest {

    private static final Long USER_ID = 1L;
    private static final Long SKU_ID = 99L;

    @Mock
    private CartService cartService;
    @Mock
    private ProductService productService;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private SkuStockQueryService skuStockQueryService;
    @Mock
    private AppUserService appUserService;
    @Mock
    private LambdaQueryChainWrapper<Cart> cartQuery;

    private CartAppServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), Cart.class);

        service = new CartAppServiceImpl();
        ReflectionTestUtils.setField(service, "cartService", cartService);
        ReflectionTestUtils.setField(service, "productService", productService);
        ReflectionTestUtils.setField(service, "productSkuService", productSkuService);
        ReflectionTestUtils.setField(service, "skuStockQueryService", skuStockQueryService);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "effectivePriceService", new com.jinlin24th.jinlin.service.EffectivePriceService());
        // 买家身份默认取不到 → 按非会员（售价），本用例只关注库存
        when(appUserService.getById(any())).thenReturn(null);

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(USER_ID);
        cart.setProductId(7L);
        cart.setSkuId(SKU_ID);
        cart.setQuantity(2);

        when(cartService.lambdaQuery()).thenReturn(cartQuery);
        when(cartQuery.eq(any(), any())).thenReturn(cartQuery);
        when(cartQuery.orderByDesc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class)))
            .thenReturn(cartQuery);
        when(cartQuery.list()).thenReturn(List.of(cart));

        Product product = new Product();
        product.setId(7L);
        product.setName("商品A");
        when(productService.listByIds(anyCollection())).thenReturn(List.of(product));

        ProductSku sku = new ProductSku();
        sku.setId(SKU_ID);
        sku.setSkuName("规格A");
        sku.setPrice(new BigDecimal("69.90"));
        sku.setStock(999); // 诱饵值：不随销售变化
        when(productSkuService.listByIds(anyCollection())).thenReturn(List.of(sku));
    }

    @Test
    @DisplayName("购物车库存必须来自 inventory 汇总，而不是 product_sku.stock")
    void cartStockShouldComeFromInventory() {
        when(skuStockQueryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 3));

        List<CartVO> carts = service.list(USER_ID);

        assertEquals(1, carts.size());
        assertEquals(3, carts.get(0).getStock(),
            "购物车展示/加购上限必须是真实可售库存，否则卖光仍能加购");
    }

    @Test
    @DisplayName("无 inventory 记录时购物车库存为 0（不可售）")
    void cartStockShouldBeZeroWithoutInventoryRecord() {
        when(skuStockQueryService.stockBySkuIds(anyCollection())).thenReturn(Map.of());

        List<CartVO> carts = service.list(USER_ID);

        assertEquals(0, carts.get(0).getStock(),
            "无库存记录等于不可售，不能沿用 product_sku.stock 的 999");
    }
}
