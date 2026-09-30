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
import com.jinlin24th.jinlin.service.EffectivePriceService;
import com.jinlin24th.jinlin.service.InventoryMaintenanceService;
import com.jinlin24th.jinlin.service.ProductSkuService;
import com.jinlin24th.jinlin.service.SkuStockQueryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    private ProductSkuService productSkuService;

    @Autowired
    private ProductCacheService productCacheService;

    /**
     * 可售库存查询（唯一事实来源 = inventory）。
     * <p>
     * 刻意不注入 InventoryService：InventoryServiceImpl 构造期依赖 ProductSkuService，
     * 反向依赖会形成循环引用（Spring Boot 2.6+ 默认禁止，启动即失败）。
     */
    @Autowired
    private SkuStockQueryService skuStockQueryService;

    /** 会员价规则唯一事实来源。 */
    @Autowired
    private EffectivePriceService effectivePriceService;

    /** 建 SKU 后补建库存行（只依赖 Mapper，无循环引用）。 */
    @Autowired
    private InventoryMaintenanceService inventoryMaintenanceService;

    @Override
    public IPage<ProductVO> adminPage(long page, long size, Integer status, Long categoryId, String keyword) {
        Page<Product> p = new Page<>(page, size);
        IPage<Product> entityPage = lambdaQuery()
            .eq(status != null, Product::getStatus, status)
            .eq(categoryId != null, Product::getCategoryId, categoryId)
            .and(keyword != null && !keyword.isBlank(), w -> w
                .like(Product::getName, keyword)
                .or()
                .like(Product::getSubtitle, keyword)
                .or()
                .like(Product::getEffects, keyword))
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
            // 同样要补建库存行：这条路径（商品页建商品顺带建"默认"规格）
            // 最容易被遗漏，漏了就会重现"上架了但没有库存记录"的坑。
            inventoryMaintenanceService.ensureInventoryForSku(sku.getId());
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
        if (dto.getEffects() != null) {
            product.setEffects(dto.getEffects());
        }
        if (dto.getPrecautions() != null) {
            product.setPrecautions(dto.getPrecautions());
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
    public IPage<ProductVO> userPage(long page, long size, Long categoryId, String keyword) {
        Page<Product> p = new Page<>(page, size);
        IPage<Product> entityPage = lambdaQuery()
            .eq(Product::getStatus, 1)
            .eq(Product::getDeleted, 0)
            .eq(categoryId != null, Product::getCategoryId, categoryId)
            .and(keyword != null && !keyword.isBlank(), w -> w
                .like(Product::getName, keyword)
                .or()
                .like(Product::getSubtitle, keyword)
                .or()
                .like(Product::getEffects, keyword))
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
        if (cached == null) {
            List<ProductSku> skus = productSkuService.lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .eq(ProductSku::getStatus, 1)
                .orderByDesc(ProductSku::getId)
                .list();
            List<ProductSkuVO> fresh = skus.stream().map(s -> {
                ProductSkuVO vo = new ProductSkuVO();
                BeanUtils.copyProperties(s, vo);
                return vo;
            }).collect(Collectors.toList());
            // 缓存只放静态属性（规格名/原价/会员价/图片），**不放库存，也不放成交价**：
            // - 库存随下单实时变化，放进 30 分钟缓存会让"余量"再次陈旧；
            // - 成交价虽然在本接口对所有人生效（= 售价），但它是"因人而异"的派生值，
            //   一旦被固化进共享缓存，将来引入按身份定价就会立刻串号。
            // 下面无论命中还是未命中都实时重算，因此 Redis 里的历史脏条目也不会造成误导。
            productCacheService.cacheUserSkus(productId, withoutVolatileFields(fresh));
            return withLiveFields(fresh);
        }
        return withLiveFields(cached);
    }

    /**
     * 复制一份并清空"易变/因人而异"字段，用于写入缓存。
     * <p>
     * 缓存里只允许存在静态属性；库存与成交价必须每次实时计算，
     * 否则会出现"余量不变"或"非会员看到会员成交价"这类串号问题。
     */
    private List<ProductSkuVO> withoutVolatileFields(List<ProductSkuVO> vos) {
        return vos.stream().map(v -> {
            ProductSkuVO copy = new ProductSkuVO();
            BeanUtils.copyProperties(v, copy);
            copy.setStock(null);
            copy.setEffectivePrice(null);
            return copy;
        }).collect(Collectors.toList());
    }

    /**
     * 覆盖实时字段：库存（inventory 汇总）与成交价。
     * <p>
     * <b>成交价为什么是"售价"</b>：本接口是公开的（`/user/product/**` 在鉴权白名单里），
     * 后端**无法知道浏览者是否分销会员**。按方案①的约定，此处一律按非会员价（售价）下发，
     * 同时保留 memberPrice 供前端展示"分销会员 ¥X"的营销提示；
     * 分销会员的实际成交价在**购物车/结算页**（需登录，能识别身份）体现。
     * <p>
     * 每次都返回新对象，避免改写缓存实例。
     */
    private List<ProductSkuVO> withLiveFields(List<ProductSkuVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> stockBySku = skuStockQueryService.stockBySkuIds(
            vos.stream().map(ProductSkuVO::getId).filter(Objects::nonNull).toList());
        return vos.stream().map(v -> {
            ProductSkuVO copy = new ProductSkuVO();
            BeanUtils.copyProperties(v, copy);
            copy.setStock(v.getId() == null ? 0 : stockBySku.getOrDefault(v.getId(), 0));
            // 公开接口按非会员定价（distributorMember = false）
            if (v.getPrice() != null) {
                copy.setEffectivePrice(effectivePriceService.effectivePrice(toSkuForPricing(v), false));
            }
            return copy;
        }).collect(Collectors.toList());
    }

    /** 用 VO 里的价格字段构造一个仅用于定价计算的轻量 SKU。 */
    private ProductSku toSkuForPricing(ProductSkuVO vo) {
        ProductSku sku = new ProductSku();
        sku.setId(vo.getId());
        sku.setPrice(vo.getPrice());
        sku.setMemberPrice(vo.getMemberPrice());
        return sku;
    }
}
