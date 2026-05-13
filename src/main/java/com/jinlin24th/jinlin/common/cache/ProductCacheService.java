package com.jinlin24th.jinlin.common.cache;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.pojo.vo.ProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 商品缓存服务。
 * <p>
 * 缓存策略：
 * 1) 商品详情、用户端商品 VO、SKU 列表均设置 TTL，避免永久缓存；
 * 2) 查询不到商品时写入短 TTL 空值缓存，防止缓存穿透；
 * 3) 商品或 SKU 更新/删除后主动删除相关缓存，保证最终一致性。
 */
@Slf4j
@Component
public class ProductCacheService {

    private static final String NULL_VALUE = "__NULL__";
    private static final String ENTITY_KEY_PREFIX = RedisUtil.KEY_PREFIX + "product:entity:";
    private static final String DETAIL_KEY_PREFIX = RedisUtil.KEY_PREFIX + "product:detail:";
    private static final String SKUS_KEY_PREFIX = RedisUtil.KEY_PREFIX + "product:skus:";

    private final RedisUtil redisUtil;
    private final Duration entityTtl;
    private final Duration detailTtl;
    private final Duration skuTtl;
    private final Duration nullTtl;

    public ProductCacheService(
        RedisUtil redisUtil,
        @Value("${app.cache.product-entity-ttl-ms:1800000}") long entityTtlMs,
        @Value("${app.cache.product-detail-ttl-ms:1800000}") long detailTtlMs,
        @Value("${app.cache.product-sku-ttl-ms:1800000}") long skuTtlMs,
        @Value("${app.cache.null-ttl-ms:60000}") long nullTtlMs
    ) {
        this.redisUtil = redisUtil;
        this.entityTtl = Duration.ofMillis(entityTtlMs);
        this.detailTtl = Duration.ofMillis(detailTtlMs);
        this.skuTtl = Duration.ofMillis(skuTtlMs);
        this.nullTtl = Duration.ofMillis(nullTtlMs);
    }

    /**
     * 查询商品实体缓存，命中空值缓存时返回 null。
     */
    public Product getProduct(Long productId) {
        return parseObject(entityKey(productId), Product.class);
    }

    /**
     * 写入商品实体缓存。
     */
    public void cacheProduct(Long productId, Product product) {
        if (productId == null || product == null) {
            return;
        }
        redisUtil.set(entityKey(productId), JSON.toJSONString(product), entityTtl);
    }

    /**
     * 写入商品实体空值缓存，防止不存在 ID 被反复打到数据库。
     */
    public void cacheNullProduct(Long productId) {
        if (productId != null) {
            redisUtil.set(entityKey(productId), NULL_VALUE, nullTtl);
        }
    }

    /**
     * 判断商品实体缓存 key 是否存在，配合空值缓存区分“没缓存”和“缓存为空”。
     */
    public boolean hasProductCache(Long productId) {
        return productId != null && redisUtil.exists(entityKey(productId));
    }

    /**
     * 读取用户端商品详情 VO 缓存。
     */
    public ProductVO getUserProductVO(Long productId) {
        return parseObject(detailKey(productId), ProductVO.class);
    }

    /**
     * 判断用户端商品详情缓存 key 是否存在。
     */
    public boolean hasUserProductVOCache(Long productId) {
        return productId != null && redisUtil.exists(detailKey(productId));
    }

    /**
     * 写入用户端商品详情空值缓存。
     */
    public void cacheNullUserProductVO(Long productId) {
        if (productId != null) {
            redisUtil.set(detailKey(productId), NULL_VALUE, nullTtl);
        }
    }

    /**
     * 写入用户端商品详情缓存。
     */
    public void cacheUserProductVO(Long productId, ProductVO vo) {
        if (productId == null || vo == null) {
            return;
        }
        redisUtil.set(detailKey(productId), JSON.toJSONString(vo), detailTtl);
    }

    /**
     * 读取用户端 SKU 列表缓存，遇到脏 JSON 会删除缓存并返回 null。
     */
    public List<ProductSkuVO> getUserSkus(Long productId) {
        String json = redisUtil.get(skusKey(productId));
        if (NULL_VALUE.equals(json)) {
            return List.of();
        }
        if (!StrUtil.isBlank(json)) {
            try {
                return JSON.parseArray(json, ProductSkuVO.class);
            } catch (Exception e) {
                log.warn("商品 SKU 缓存解析失败，将删除脏缓存, productId={}", productId, e);
                redisUtil.delete(skusKey(productId));
            }
        }
        return null;
    }

    /**
     * 写入用户端 SKU 列表缓存，空列表也会缓存，降低穿透风险。
     */
    public void cacheUserSkus(Long productId, List<ProductSkuVO> skus) {
        if (productId == null || skus == null) {
            return;
        }
        redisUtil.set(skusKey(productId), JSON.toJSONString(skus), skuTtl);
    }

    /**
     * 删除商品相关缓存。
     */
    public void evict(Long productId) {
        if (productId == null) {
            return;
        }
        redisUtil.delete(entityKey(productId), detailKey(productId), skusKey(productId));
    }

    /**
     * 统一解析对象缓存，支持空值缓存和脏缓存清理。
     */
    private <T> T parseObject(String key, Class<T> clazz) {
        String json = redisUtil.get(key);
        if (NULL_VALUE.equals(json)) {
            return null;
        }
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, clazz);
        } catch (Exception e) {
            log.warn("商品缓存解析失败，将删除脏缓存, key={}", key, e);
            redisUtil.delete(key);
            return null;
        }
    }

    private static String entityKey(Long productId) {
        return ENTITY_KEY_PREFIX + Optional.ofNullable(productId).orElse(-1L);
    }

    private static String detailKey(Long productId) {
        return DETAIL_KEY_PREFIX + Optional.ofNullable(productId).orElse(-1L);
    }

    private static String skusKey(Long productId) {
        return SKUS_KEY_PREFIX + Optional.ofNullable(productId).orElse(-1L);
    }
}
