package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.common.cache.ProductCacheService;
import com.jinlin24th.jinlin.pojo.dto.ProductSkuDTO;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.mapper.ProductSkuMapper;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.service.InventoryMaintenanceService;
import com.jinlin24th.jinlin.service.ProductSkuService;
import com.jinlin24th.jinlin.service.SkuStockQueryService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductSkuServiceImpl extends ServiceImpl<ProductSkuMapper, ProductSku>
    implements ProductSkuService {

    private final ProductCacheService productCacheService;

    /**
     * 可售库存查询（唯一事实来源 = inventory 表）。
     * <p>
     * 这里刻意不注入 InventoryService：InventoryServiceImpl 构造期依赖 ProductSkuService，
     * 反向依赖会形成循环引用（Spring Boot 2.6+ 默认禁止，启动即失败）。
     * SkuStockQueryService 只依赖 Mapper，因此没有环。
     */
    private final SkuStockQueryService skuStockQueryService;

    /**
     * 建 SKU 后补建库存行。
     * <p>
     * 只依赖 Mapper（不依赖 InventoryService，避免与其构造期依赖 ProductSkuService 形成循环引用）。
     */
    private final InventoryMaintenanceService inventoryMaintenanceService;

    public ProductSkuServiceImpl(ProductCacheService productCacheService,
                                SkuStockQueryService skuStockQueryService,
                                InventoryMaintenanceService inventoryMaintenanceService) {
        this.productCacheService = productCacheService;
        this.skuStockQueryService = skuStockQueryService;
        this.inventoryMaintenanceService = inventoryMaintenanceService;
    }

    @Override
    public IPage<ProductSkuVO> adminPage(long page, long size, Long productId, Integer status, String keyword) {
        Page<ProductSku> p = new Page<>(page, size);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        IPage<ProductSku> entityPage = lambdaQuery()
            .eq(productId != null, ProductSku::getProductId, productId)
            .eq(status != null, ProductSku::getStatus, status)
            .like(StringUtils.hasText(normalizedKeyword), ProductSku::getSkuName, normalizedKeyword)
            .orderByDesc(ProductSku::getId)
            .page(p);
        Page<ProductSkuVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

        // 库存必须展示 inventory 的实时汇总：product_sku.stock 不随销售变化，
        // 运营照它补货会被误导。无库存记录 → 0（不可售），不回退到旧值。
        Map<Long, Integer> stockBySku = skuStockQueryService.stockBySkuIds(
            entityPage.getRecords().stream().map(ProductSku::getId).toList());

        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            ProductSkuVO vo = new ProductSkuVO();
            BeanUtils.copyProperties(e, vo);
            vo.setStock(stockBySku.getOrDefault(e.getId(), 0));
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ProductSkuVO getVO(Long id) {
        ProductSku sku = getById(id);
        if (sku == null) {
            return null;
        }
        ProductSkuVO vo = new ProductSkuVO();
        BeanUtils.copyProperties(sku, vo);
        return vo;
    }

    @Override
    public ProductSkuVO create(ProductSkuDTO dto) {
        ProductSku sku = new ProductSku();
        BeanUtils.copyProperties(dto, sku);
        save(sku);
        // 自动补建库存行：否则商品"看起来已上架、实际库存为 0"，
        // 用户看到无货而运营以为已上架。失败不影响上架（内部已吞异常）。
        inventoryMaintenanceService.ensureInventoryForSku(sku.getId());
        productCacheService.evict(sku.getProductId());
        ProductSkuVO vo = new ProductSkuVO();
        BeanUtils.copyProperties(sku, vo);
        return vo;
    }

    @Override
    public ProductSkuVO update(Long id, ProductSkuDTO dto) {
        ProductSku sku = getById(id);
        if (sku == null) {
            return null;
        }
        if (dto.getProductId() != null) {
            sku.setProductId(dto.getProductId());
        }
        if (dto.getSkuName() != null) {
            sku.setSkuName(dto.getSkuName());
        }
        if (dto.getPrice() != null) {
            sku.setPrice(dto.getPrice());
        }
        if (dto.getMemberPrice() != null) {
            sku.setMemberPrice(dto.getMemberPrice());
        }
        if (dto.getStock() != null) {
            sku.setStock(dto.getStock());
        }
        if (dto.getSkuImage() != null) {
            sku.setSkuImage(dto.getSkuImage());
        }
        if (dto.getStatus() != null) {
            sku.setStatus(dto.getStatus());
        }
        updateById(sku);
        productCacheService.evict(sku.getProductId());
        ProductSkuVO vo = new ProductSkuVO();
        BeanUtils.copyProperties(sku, vo);
        return vo;
    }

    @Override
    public Boolean delete(Long id) {
        ProductSku sku = getById(id);
        boolean ok = removeById(id);
        if (ok && sku != null) {
            productCacheService.evict(sku.getProductId());
        }
        return ok;
    }
}
