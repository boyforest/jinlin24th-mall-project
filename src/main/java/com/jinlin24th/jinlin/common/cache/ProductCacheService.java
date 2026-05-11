package com.jinlin24th.jinlin.common.cache;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.pojo.vo.ProductVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class ProductCacheService {

    private static final String DETAIL_KEY_PREFIX = "cache:product:detail:";
    private static final String SKUS_KEY_PREFIX = "cache:product:skus:";

    private final RedisUtil redisUtil;
    private final Duration detailTtl;
    private final Duration skuTtl;

    public ProductCacheService(
        RedisUtil redisUtil,
        @Value("${app.cache.product-detail-ttl-ms:1800000}") long detailTtlMs,
        @Value("${app.cache.product-sku-ttl-ms:1800000}") long skuTtlMs
    ) {
        this.redisUtil = redisUtil;
        this.detailTtl = Duration.ofMillis(detailTtlMs);
        this.skuTtl = Duration.ofMillis(skuTtlMs);
    }

    public ProductVO getUserProductVO(Long productId) {
        String json = redisUtil.get(detailKey(productId));
        if (!StrUtil.isBlank(json)) {
            return JSON.parseObject(json, ProductVO.class);
        }
        return null;
    }

    public void cacheUserProductVO(Long productId, ProductVO vo) {
        if (productId == null || vo == null) {
            return;
        }
        redisUtil.set(detailKey(productId), JSON.toJSONString(vo), detailTtl);
    }

    public List<ProductSkuVO> getUserSkus(Long productId) {
        String json = redisUtil.get(skusKey(productId));
        if (!StrUtil.isBlank(json)) {
            return JSON.parseArray(json, ProductSkuVO.class);
        }
        return null;
    }

    public void cacheUserSkus(Long productId, List<ProductSkuVO> skus) {
        if (productId == null || skus == null) {
            return;
        }
        redisUtil.set(skusKey(productId), JSON.toJSONString(skus), skuTtl);
    }

    public void evict(Long productId) {
        if (productId == null) {
            return;
        }
        redisUtil.delete(detailKey(productId), skusKey(productId));
    }

    private static String detailKey(Long productId) {
        return DETAIL_KEY_PREFIX + Optional.ofNullable(productId).orElse(-1L);
    }

    private static String skusKey(Long productId) {
        return SKUS_KEY_PREFIX + Optional.ofNullable(productId).orElse(-1L);
    }
}
