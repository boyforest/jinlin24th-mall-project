package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.common.cache.ProductCacheService;
import com.jinlin24th.jinlin.pojo.dto.ProductSkuDTO;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.mapper.ProductSkuMapper;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.service.ProductSkuService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class ProductSkuServiceImpl extends ServiceImpl<ProductSkuMapper, ProductSku>
    implements ProductSkuService {

    private final ProductCacheService productCacheService;

    public ProductSkuServiceImpl(ProductCacheService productCacheService) {
        this.productCacheService = productCacheService;
    }

    @Override
    public IPage<ProductSkuVO> adminPage(long page, long size, Long productId, Integer status) {
        Page<ProductSku> p = new Page<>(page, size);
        IPage<ProductSku> entityPage = lambdaQuery()
            .eq(productId != null, ProductSku::getProductId, productId)
            .eq(status != null, ProductSku::getStatus, status)
            .orderByDesc(ProductSku::getId)
            .page(p);
        Page<ProductSkuVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            ProductSkuVO vo = new ProductSkuVO();
            BeanUtils.copyProperties(e, vo);
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
