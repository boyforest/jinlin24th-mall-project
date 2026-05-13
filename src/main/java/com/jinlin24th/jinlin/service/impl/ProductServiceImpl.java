package com.jinlin24th.jinlin.service.impl;

import com.jinlin24th.jinlin.common.cache.ProductCacheService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.mapper.ProductMapper;
import com.jinlin24th.jinlin.pojo.dto.ProductDTO;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.pojo.vo.ProductVO;
import com.jinlin24th.jinlin.service.ProductService;
import com.jinlin24th.jinlin.service.ProductSkuService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    private ProductSkuService productSkuService;

    @Autowired
    private ProductCacheService productCacheService;

    @Override
    public IPage<ProductVO> adminPage(long page, long size, Integer status, Long categoryId) {
        Page<Product> p = new Page<>(page, size);
        IPage<Product> entityPage = lambdaQuery()
            .eq(status != null, Product::getStatus, status)
            .eq(categoryId != null, Product::getCategoryId, categoryId)
            .orderByDesc(Product::getId)
            .page(p);
        Page<ProductVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            ProductVO vo = new ProductVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ProductVO getVO(Long id) {
        Product product = getCachedProduct(id);
        if (product == null) {
            return null;
        }
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        return vo;
    }

    @Override
    public ProductVO getUserVO(Long id) {
        ProductVO cached = productCacheService.getUserProductVO(id);
        if (cached != null || productCacheService.hasUserProductVOCache(id)) {
            return cached;
        }
        Product product = lambdaQuery()
            .eq(Product::getId, id)
            .eq(Product::getStatus, 1)
            .eq(Product::getDeleted, 0)
            .one();
        if (product == null) {
            productCacheService.cacheNullUserProductVO(id);
            return null;
        }
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        productCacheService.cacheUserProductVO(id, vo);
        return vo;
    }

    @Override
    public ProductVO create(ProductDTO dto) {
        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        product.setSales(0);
        product.setDeleted(0);
        save(product);

        if (dto.getPrice() != null) {
            ProductSku sku = new ProductSku();
            sku.setProductId(product.getId());
            sku.setSkuName("默认");
            sku.setPrice(dto.getPrice());
            sku.setMemberPrice(null);
            sku.setStock(0);
            sku.setSkuImage(product.getMainImage());
            sku.setStatus(1);
            productSkuService.save(sku);
        }

        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        return vo;
    }

    /**
     * 商品实体缓存查询。
     * <p>
     * 先查 Redis，未命中再回源数据库；数据库不存在时写入短 TTL 空值缓存，防止缓存穿透。
     */
    private Product getCachedProduct(Long id) {
        Product cached = productCacheService.getProduct(id);
        if (cached != null || productCacheService.hasProductCache(id)) {
            return cached;
        }

        Product product = lambdaQuery()
            .eq(Product::getId, id)
            .one();
        if (product == null) {
            productCacheService.cacheNullProduct(id);
            return null;
        }
        productCacheService.cacheProduct(id, product);
        return product;
    }

    @Override
    public ProductVO update(Long id, ProductDTO dto) {
        Product product = getById(id);
        if (product == null) {
            return null;
        }
        if (dto.getCategoryId() != null) {
            product.setCategoryId(dto.getCategoryId());
        }
        if (dto.getName() != null) {
            product.setName(dto.getName());
        }
        if (dto.getSubtitle() != null) {
            product.setSubtitle(dto.getSubtitle());
        }
        if (dto.getMainImage() != null) {
            product.setMainImage(dto.getMainImage());
        }
        if (dto.getImages() != null) {
            product.setImages(dto.getImages());
        }
        if (dto.getVideoUrl() != null) {
            product.setVideoUrl(dto.getVideoUrl());
        }
        if (dto.getDetail() != null) {
            product.setDetail(dto.getDetail());
        }
        if (dto.getStatus() != null) {
            product.setStatus(dto.getStatus());
        }
        if (dto.getSort() != null) {
            product.setSort(dto.getSort());
        }
        updateById(product);

        if (dto.getPrice() != null) {
            ProductSku defaultSku = productSkuService.lambdaQuery()
                .eq(ProductSku::getProductId, id)
                .eq(ProductSku::getSkuName, "默认")
                .last("limit 1")
                .one();
            if (defaultSku != null && !Objects.equals(defaultSku.getPrice(), dto.getPrice())) {
                defaultSku.setPrice(dto.getPrice());
                productSkuService.updateById(defaultSku);
            }
        }

        productCacheService.evict(id);

        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        return vo;
    }

    @Override
    public Boolean delete(Long id) {
        boolean ok = removeById(id);
        if (ok) {
            productCacheService.evict(id);
        }
        return ok;
    }

    @Override
    public IPage<ProductVO> userPage(long page, long size, Long categoryId) {
        Page<Product> p = new Page<>(page, size);
        IPage<Product> entityPage = lambdaQuery()
            .eq(Product::getStatus, 1)
            .eq(Product::getDeleted, 0)
            .eq(categoryId != null, Product::getCategoryId, categoryId)
            .orderByDesc(Product::getSort)
            .orderByDesc(Product::getId)
            .page(p);

        Page<ProductVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            ProductVO vo = new ProductVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<ProductSkuVO> userSkus(Long productId) {
        List<ProductSkuVO> cached = productCacheService.getUserSkus(productId);
        if (cached != null) {
            return cached;
        }
        List<ProductSku> skus = productSkuService.lambdaQuery()
            .eq(ProductSku::getProductId, productId)
            .eq(ProductSku::getStatus, 1)
            .orderByDesc(ProductSku::getId)
            .list();
        List<ProductSkuVO> vos = skus.stream().map(s -> {
            ProductSkuVO vo = new ProductSkuVO();
            BeanUtils.copyProperties(s, vo);
            return vo;
        }).collect(Collectors.toList());
        productCacheService.cacheUserSkus(productId, vos);
        return vos;
    }
}
