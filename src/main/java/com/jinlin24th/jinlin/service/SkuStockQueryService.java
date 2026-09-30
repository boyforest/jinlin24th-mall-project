package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinlin24th.jinlin.mapper.InventoryMapper;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SKU 可售库存查询（唯一事实来源 = {@code inventory} 表）。
 * <p>
 * <b>为什么单独抽一个组件，而不是放进 InventoryService：</b>
 * {@code InventoryServiceImpl} 构造期依赖 {@code ProductSkuService}，
 * 而本类的调用方（ProductSkuServiceImpl / ProductServiceImpl / CartAppServiceImpl）
 * 若反向依赖 InventoryService 就会形成循环依赖；Spring Boot 2.6+ 默认禁止循环引用，
 * 会在启动时直接失败。本组件只依赖 Mapper，因此没有环。
 * <p>
 * <b>为什么必须用它而不是 {@code product_sku.stock}：</b>
 * 订单扣减的是 {@code inventory.stock}（按仓库），
 * {@code product_sku.stock} 从不随销售变化——展示它会让用户看到永远不变的"余量"，
 * 卖光了仍能加购，直到结算才失败。
 * <p>
 * 语义说明：SUM 跨全部仓库，与下单时的库存分配保持一致（都不过滤仓库状态）。
 * 若将来给 {@code warehouse.status} 加上"停用仓不参与分配"的规则，这里必须同步，
 * 否则展示与能否下单会再次脱节。
 */
@Component
public class SkuStockQueryService {

    private final InventoryMapper inventoryMapper;

    public SkuStockQueryService(InventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    /**
     * 批量汇总各 SKU 的可售库存（跨仓库相加）。
     *
     * @param skuIds 待查询的 SKU ID 集合，可为 null/空（直接返回空 Map，不查库）
     * @return skuId → 可售总量；无库存记录的 SKU 不会出现在结果中（调用方按 0 处理）
     */
    public Map<Long, Integer> stockBySkuIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> distinctIds = skuIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        List<Inventory> rows = inventoryMapper.selectList(
            new LambdaQueryWrapper<Inventory>()
                .select(Inventory::getSkuId, Inventory::getStock)
                .in(Inventory::getSkuId, distinctIds));
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> stockBySku = new HashMap<>();
        for (Inventory row : rows) {
            if (row == null || row.getSkuId() == null) {
                continue;
            }
            stockBySku.merge(row.getSkuId(), row.getStock() == null ? 0 : row.getStock(), Integer::sum);
        }
        return stockBySku;
    }

    /**
     * 单个 SKU 的可售库存；无记录时为 0。
     */
    public int stockBySkuId(Long skuId) {
        if (skuId == null) {
            return 0;
        }
        return stockBySkuIds(List.of(skuId)).getOrDefault(skuId, 0);
    }
}
