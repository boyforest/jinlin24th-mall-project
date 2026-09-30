package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.jinlin24th.jinlin.common.cache.ProductCacheService;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.service.ProductSkuService;
import com.jinlin24th.jinlin.service.SkuStockQueryService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 端商品详情 SKU 库存展示测试。
 * <p>
 * 这是最容易被缓存坑到的一条路径：SKU 列表被缓存 30 分钟，
 * 早期实现把 {@code product_sku.stock} 一起缓存进去，于是：
 * <ol>
 *   <li>展示的库存本身就不随销售变化（诱饵字段）；</li>
 *   <li>即便改成实时库存，一旦写进缓存又会陈旧 30 分钟。</li>
 * </ol>
 * 因此约定：<b>缓存只放静态属性，库存每次实时从 inventory 填充</b>。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductUserSkusStockTest {

    private static final Long PRODUCT_ID = 7L;
    private static final Long SKU_ID = 99L;

    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductCacheService productCacheService;
    @Mock
    private SkuStockQueryService skuStockQueryService;
    @Mock
    private LambdaQueryChainWrapper<ProductSku> skuQuery;

    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProductSku.class);

        service = new ProductServiceImpl();
        ReflectionTestUtils.setField(service, "productSkuService", productSkuService);
        ReflectionTestUtils.setField(service, "productCacheService", productCacheService);
        ReflectionTestUtils.setField(service, "skuStockQueryService", skuStockQueryService);
        ReflectionTestUtils.setField(service, "effectivePriceService", new com.jinlin24th.jinlin.service.EffectivePriceService());

        when(productSkuService.lambdaQuery()).thenReturn(skuQuery);
        when(skuQuery.eq(any(), any())).thenReturn(skuQuery);
        when(skuQuery.orderByDesc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class)))
            .thenReturn(skuQuery);
        when(skuQuery.list()).thenReturn(List.of(skuEntity(200)));
    }

    private ProductSku skuEntity(int staleStock) {
        ProductSku sku = new ProductSku();
        sku.setId(SKU_ID);
        sku.setProductId(PRODUCT_ID);
        sku.setSkuName("规格A");
        sku.setPrice(new BigDecimal("69.90"));
        sku.setStatus(1);
        sku.setStock(staleStock); // 诱饵值：不随销售变化
        return sku;
    }

    private ProductSkuVO cachedVo(Integer stock) {
        ProductSkuVO vo = new ProductSkuVO();
        vo.setId(SKU_ID);
        vo.setProductId(PRODUCT_ID);
        vo.setSkuName("规格A");
        vo.setPrice(new BigDecimal("69.90"));
        vo.setStatus(1);
        vo.setStock(stock);
        return vo;
    }

    @Test
    @DisplayName("缓存未命中：库存来自 inventory 汇总，而不是 product_sku.stock")
    void cacheMissShouldUseInventoryStock() {
        when(productCacheService.getUserSkus(PRODUCT_ID)).thenReturn(null);
        when(skuStockQueryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 5));

        List<ProductSkuVO> vos = service.userSkus(PRODUCT_ID);

        assertEquals(1, vos.size());
        assertEquals(5, vos.get(0).getStock(), "展示库存必须是真实可售量");
    }

    @Test
    @DisplayName("写进缓存的副本不得携带库存（否则 30 分钟内都是旧值）")
    void cachePayloadMustNotCarryStock() {
        when(productCacheService.getUserSkus(PRODUCT_ID)).thenReturn(null);
        when(skuStockQueryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 5));

        service.userSkus(PRODUCT_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductSkuVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(productCacheService).cacheUserSkus(any(), captor.capture());

        assertNull(captor.getValue().get(0).getStock(),
            "库存是易变字段，不能固化进 30 分钟缓存");
    }

    @Test
    @DisplayName("缓存命中时仍必须实时填充库存（陈旧缓存的回归）")
    void cacheHitMustStillFillLiveStock() {
        // Redis 里是旧条目，带着下单前的旧库存 200
        when(productCacheService.getUserSkus(PRODUCT_ID)).thenReturn(List.of(cachedVo(200)));
        when(skuStockQueryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 3));

        List<ProductSkuVO> vos = service.userSkus(PRODUCT_ID);

        assertEquals(3, vos.get(0).getStock(),
            "缓存命中也要用 inventory 覆盖，否则用户会看到已卖光的货还能买");
    }

    @Test
    @DisplayName("不得改写缓存实例（避免把实时值写回共享对象）")
    void mustNotMutateCachedInstance() {
        ProductSkuVO cached = cachedVo(200);
        when(productCacheService.getUserSkus(PRODUCT_ID)).thenReturn(List.of(cached));
        when(skuStockQueryService.stockBySkuIds(anyCollection())).thenReturn(Map.of(SKU_ID, 3));

        List<ProductSkuVO> vos = service.userSkus(PRODUCT_ID);

        assertEquals(200, cached.getStock(), "缓存实例不应被就地修改");
        assertTrue(vos.get(0) != cached, "应返回新对象");
    }

    @Test
    @DisplayName("无库存记录时展示 0（不可售），不回退到旧值")
    void noInventoryRecordShouldMeanZero() {
        when(productCacheService.getUserSkus(PRODUCT_ID)).thenReturn(List.of(cachedVo(200)));
        when(skuStockQueryService.stockBySkuIds(anyCollection())).thenReturn(Map.of());

        List<ProductSkuVO> vos = service.userSkus(PRODUCT_ID);

        assertEquals(0, vos.get(0).getStock());
    }
}
