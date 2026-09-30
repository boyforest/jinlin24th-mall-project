package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.jinlin24th.jinlin.common.cache.ProductCacheService;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.Cart;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.vo.CartVO;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.CartService;
import com.jinlin24th.jinlin.service.EffectivePriceService;
import com.jinlin24th.jinlin.service.ProductService;
import com.jinlin24th.jinlin.service.ProductSkuService;
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
 * 价格展示与身份一致性测试（方案①：可见会员价，但只有分销会员按会员价成交）。
 * <p>
 * 关键不变量：**前端展示的成交价（effectivePrice）必须等于后端实际结算价**。
 * 此前小程序在 6 处自行判断 `memberPrice || price`，而后端无条件取会员价 ——
 * 规则一旦改为"因人而异"，任何一处不对齐都会出现"显示 A 价、结算 B 价"。
 * <p>
 * 因此约定：`effectivePrice` 由后端按身份算好下发，前端只显示，不自行判断。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceDisplayTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long SKU_ID = 99L;
    private static final String LIST_PRICE = "69.90";
    private static final String MEMBER_PRICE = "59.90";

    // ── 购物车 ──
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

    private CartAppServiceImpl cartServiceUnderTest;

    // ── C 端商品规格（公开接口） ──
    @Mock
    private ProductCacheService productCacheService;
    @Mock
    private LambdaQueryChainWrapper<ProductSku> skuQuery;

    private ProductServiceImpl productServiceUnderTest;

    private ProductSku sku() {
        ProductSku sku = new ProductSku();
        sku.setId(SKU_ID);
        sku.setProductId(PRODUCT_ID);
        sku.setSkuName("规格A");
        sku.setPrice(new BigDecimal(LIST_PRICE));
        sku.setMemberPrice(new BigDecimal(MEMBER_PRICE));
        sku.setStatus(1);
        return sku;
    }

    private AppUser user(Integer isDistributor) {
        AppUser u = new AppUser();
        u.setId(USER_ID);
        u.setIsDistributor(isDistributor);
        u.setStatus(1);
        return u;
    }

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), Cart.class);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProductSku.class);

        // 购物车
        cartServiceUnderTest = new CartAppServiceImpl();
        ReflectionTestUtils.setField(cartServiceUnderTest, "cartService", cartService);
        ReflectionTestUtils.setField(cartServiceUnderTest, "productService", productService);
        ReflectionTestUtils.setField(cartServiceUnderTest, "productSkuService", productSkuService);
        ReflectionTestUtils.setField(cartServiceUnderTest, "skuStockQueryService", skuStockQueryService);
        ReflectionTestUtils.setField(cartServiceUnderTest, "appUserService", appUserService);
        ReflectionTestUtils.setField(cartServiceUnderTest, "effectivePriceService", new EffectivePriceService());

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(USER_ID);
        cart.setProductId(PRODUCT_ID);
        cart.setSkuId(SKU_ID);
        cart.setQuantity(2);
        when(cartService.lambdaQuery()).thenReturn(cartQuery);
        when(cartQuery.eq(any(), any())).thenReturn(cartQuery);
        when(cartQuery.orderByDesc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class)))
            .thenReturn(cartQuery);
        when(cartQuery.list()).thenReturn(List.of(cart));

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("商品A");
        when(productService.listByIds(anyCollection())).thenReturn(List.of(product));
        when(productSkuService.listByIds(anyCollection())).thenReturn(List.of(sku()));
        when(skuStockQueryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 50));

        // C 端商品规格
        productServiceUnderTest = new ProductServiceImpl();
        ReflectionTestUtils.setField(productServiceUnderTest, "productSkuService", productSkuService);
        ReflectionTestUtils.setField(productServiceUnderTest, "productCacheService", productCacheService);
        ReflectionTestUtils.setField(productServiceUnderTest, "skuStockQueryService", skuStockQueryService);
        ReflectionTestUtils.setField(productServiceUnderTest, "effectivePriceService", new EffectivePriceService());
        when(productSkuService.lambdaQuery()).thenReturn(skuQuery);
        when(skuQuery.eq(any(), any())).thenReturn(skuQuery);
        when(skuQuery.orderByDesc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class)))
            .thenReturn(skuQuery);
        when(skuQuery.list()).thenReturn(List.of(sku()));
        when(skuStockQueryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 50));
    }

    private BigDecimal effectiveOf(List<CartVO> carts) {
        return carts.get(0).getEffectivePrice();
    }

    // ── 购物车（已登录，能准确判断身份） ──

    @Test
    @DisplayName("购物车：分销会员的成交价为会员价")
    void cartMemberShouldSeeMemberPrice() {
        when(appUserService.getById(USER_ID)).thenReturn(user(1));

        List<CartVO> carts = cartServiceUnderTest.list(USER_ID);

        assertEquals(0, new BigDecimal(MEMBER_PRICE).compareTo(effectiveOf(carts)));
        assertEquals(0, new BigDecimal(LIST_PRICE).compareTo(carts.get(0).getPrice()),
            "原价仍要下发，供前端展示划线价");
    }

    @Test
    @DisplayName("购物车：非分销会员的成交价为售价（不得显示会员价作为成交价）")
    void cartNonMemberShouldSeeListPrice() {
        when(appUserService.getById(USER_ID)).thenReturn(user(0));

        List<CartVO> carts = cartServiceUnderTest.list(USER_ID);

        assertEquals(0, new BigDecimal(LIST_PRICE).compareTo(effectiveOf(carts)),
            "非分销会员的成交价必须是售价，否则前端会把会员价当合计算，出现少收款");
    }

    @Test
    @DisplayName("购物车：拿不到用户身份时保守按售价")
    void cartWithoutIdentityShouldUseListPrice() {
        when(appUserService.getById(USER_ID)).thenReturn(null);

        List<CartVO> carts = cartServiceUnderTest.list(USER_ID);

        assertEquals(0, new BigDecimal(LIST_PRICE).compareTo(effectiveOf(carts)));
    }

    // ── C 端商品规格（公开接口，无法知道浏览者身份） ──

    @Test
    @DisplayName("公开商品接口：成交价一律是售价（因为它不知道浏览者是谁）")
    void publicSkuEndpointShouldExposeListPriceAsEffective() {
        when(productCacheService.getUserSkus(PRODUCT_ID)).thenReturn(null);

        List<ProductSkuVO> vos = productServiceUnderTest.userSkus(PRODUCT_ID);

        assertEquals(0, new BigDecimal(LIST_PRICE).compareTo(vos.get(0).getEffectivePrice()),
            "公开接口无法识别身份，成交价必须是售价（会员价在购物车/结算页才体现）");
    }

    @Test
    @DisplayName("公开商品接口：仍下发会员价，供前端展示'分销会员 ¥X'的营销提示（方案①）")
    void publicSkuEndpointShouldStillExposeMemberPriceForHint() {
        when(productCacheService.getUserSkus(PRODUCT_ID)).thenReturn(null);

        List<ProductSkuVO> vos = productServiceUnderTest.userSkus(PRODUCT_ID);

        assertEquals(0, new BigDecimal(MEMBER_PRICE).compareTo(vos.get(0).getMemberPrice()),
            "方案①允许把会员价作为营销钩子展示给所有人");
    }

    @Test
    @DisplayName("公开商品接口缓存命中时，成交价仍必须是售价（防缓存串号）")
    void cacheHitMustNotLeakMemberPriceAsEffective() {
        ProductSkuVO cached = new ProductSkuVO();
        cached.setId(SKU_ID);
        cached.setProductId(PRODUCT_ID);
        cached.setPrice(new BigDecimal(LIST_PRICE));
        cached.setMemberPrice(new BigDecimal(MEMBER_PRICE));
        // 模拟 Redis 里的旧条目已被写入过会员价作为成交价（历史脏数据）
        cached.setEffectivePrice(new BigDecimal(MEMBER_PRICE));
        when(productCacheService.getUserSkus(PRODUCT_ID)).thenReturn(List.of(cached));

        List<ProductSkuVO> vos = productServiceUnderTest.userSkus(PRODUCT_ID);

        assertEquals(0, new BigDecimal(LIST_PRICE).compareTo(vos.get(0).getEffectivePrice()),
            "即使缓存里有脏的成交价，也必须按公开接口规则覆盖为售价，不能让非会员看到会员成交价");
    }
}
