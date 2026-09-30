package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jinlin24th.jinlin.common.cache.ProductCacheService;
import com.jinlin24th.jinlin.mapper.ProductSkuMapper;
import com.jinlin24th.jinlin.pojo.dto.ProductDTO;
import com.jinlin24th.jinlin.pojo.dto.ProductSkuDTO;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.service.InventoryMaintenanceService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "建 SKU 自动创建库存行"的接线测试。
 * <p>
 * 建 SKU 有**两条**路径，必须都接上，否则会留下同一个坑：
 * <ol>
 *   <li>管理端「规格」页新建规格 → {@code ProductSkuServiceImpl.create}</li>
 *   <li>管理端「商品」页新建商品（会顺带自动建一个"默认"规格）
 *       → {@code ProductServiceImpl.create} 内的 {@code productSkuService.save(sku)}</li>
 * </ol>
 * 第 2 条是容易被遗漏的那条，因此单独覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkuInventoryAutoCreateWiringTest {

    private static final Long NEW_SKU_ID = 100L;

    @Mock
    private ProductCacheService productCacheService;
    @Mock
    private SkuStockQueryService skuStockQueryService;
    @Mock
    private InventoryMaintenanceService inventoryMaintenanceService;

    // ── 路径 1：规格页新建规格 ──
    @Mock
    private ProductSkuMapper productSkuMapper;
    private ProductSkuServiceImpl skuService;

    // ── 路径 2：商品页新建商品（自动建默认规格） ──
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private com.jinlin24th.jinlin.mapper.ProductMapper productMapper;
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProductSku.class);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), Product.class);

        // 路径 1
        skuService = new ProductSkuServiceImpl(productCacheService, skuStockQueryService,
            inventoryMaintenanceService);
        ReflectionTestUtils.setField(skuService, "baseMapper", productSkuMapper);
        // 模拟自增主键回填
        when(productSkuMapper.insert(any(ProductSku.class))).thenAnswer(inv -> {
            ProductSku s = inv.getArgument(0);
            s.setId(NEW_SKU_ID);
            return 1;
        });

        // 路径 2
        productService = new ProductServiceImpl();
        ReflectionTestUtils.setField(productService, "productSkuService", productSkuService);
        ReflectionTestUtils.setField(productService, "productCacheService", productCacheService);
        ReflectionTestUtils.setField(productService, "skuStockQueryService", skuStockQueryService);
        ReflectionTestUtils.setField(productService, "effectivePriceService",
            new com.jinlin24th.jinlin.service.EffectivePriceService());
        ReflectionTestUtils.setField(productService, "inventoryMaintenanceService",
            inventoryMaintenanceService);
        ReflectionTestUtils.setField(productService, "baseMapper", productMapper);
        // 模拟商品自增主键回填
        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> {
            Product entity = inv.getArgument(0);
            entity.setId(7L);
            return 1;
        });
        when(productSkuService.save(any(ProductSku.class))).thenAnswer(inv -> {
            ProductSku s = inv.getArgument(0);
            s.setId(NEW_SKU_ID);
            return true;
        });
    }

    @Test
    @DisplayName("路径1：规格页新建规格后，必须自动补建库存行")
    void skuPageCreationShouldTriggerInventoryEnsure() {
        ProductSkuDTO dto = new ProductSkuDTO();
        dto.setProductId(7L);
        dto.setSkuName("规格A");
        dto.setPrice(new BigDecimal("69.90"));
        dto.setStatus(1);

        skuService.create(dto);

        verify(inventoryMaintenanceService).ensureInventoryForSku(NEW_SKU_ID);
    }

    @Test
    @DisplayName("路径2：商品页新建商品（自动建默认规格）也必须补建库存行 —— 这是最容易被漏掉的一条")
    void productPageCreationShouldAlsoTriggerInventoryEnsure() {
        ProductDTO dto = new ProductDTO();
        dto.setName("商品A");
        dto.setPrice(new BigDecimal("69.90"));
        dto.setStatus(1);

        productService.create(dto);

        ArgumentCaptor<ProductSku> captor = ArgumentCaptor.forClass(ProductSku.class);
        verify(productSkuService).save(captor.capture());
        assertEquals("默认", captor.getValue().getSkuName());
        verify(inventoryMaintenanceService).ensureInventoryForSku(NEW_SKU_ID);
    }

    @Test
    @DisplayName("路径2：未填价格时不建默认规格，也就不该触发补建库存行")
    void productWithoutPriceShouldNotTriggerEnsure() {
        ProductDTO dto = new ProductDTO();
        dto.setName("无价商品");
        dto.setPrice(null);
        dto.setStatus(1);

        productService.create(dto);

        org.mockito.Mockito.verify(inventoryMaintenanceService, org.mockito.Mockito.never())
            .ensureInventoryForSku(any());
    }
}
