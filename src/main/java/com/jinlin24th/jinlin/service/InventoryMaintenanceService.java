package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinlin24th.jinlin.mapper.InventoryMapper;
import com.jinlin24th.jinlin.mapper.WarehouseMapper;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.entity.Warehouse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存维护（单仓模式判定 + 建 SKU 时补建库存行）。
 * <p>
 * <b>为什么单独抽一个只依赖 Mapper 的组件，而不放进 InventoryService：</b>
 * {@code InventoryServiceImpl} **构造期**依赖 {@code ProductSkuService}，
 * 而本组件的调用方正是 {@code ProductSkuServiceImpl} / {@code ProductServiceImpl}——
 * 若反向依赖 InventoryService 就形成循环引用，Spring Boot 2.6+ 默认禁止，
 * 会在启动时直接失败（构造器注入连"提前引用"的机会都没有）。
 * <p>
 * <b>为什么需要它：</b>上架一个商品要跨「商品 → 规格 → 库存」三个页面，
 * 建完 SKU 若忘记去库存页建记录，商品**看起来已上架、实际库存为 0**——
 * 用户看到"无货"，运营却以为已经上架。本组件在建 SKU 时自动补一行库存记录消除该问题。
 * <p>
 * <b>单仓模式为什么是"派生"而非配置项：</b>规则 = 启用仓库恰好一个。
 * 若改成配置开关，将来加第二个仓库时忘记关掉开关，库存会被静默写进错误的仓；
 * 派生规则会随仓库数量自动切换，不会遗忘。
 */
@Slf4j
@Component
public class InventoryMaintenanceService {

    private final WarehouseMapper warehouseMapper;
    private final InventoryMapper inventoryMapper;

    public InventoryMaintenanceService(WarehouseMapper warehouseMapper, InventoryMapper inventoryMapper) {
        this.warehouseMapper = warehouseMapper;
        this.inventoryMapper = inventoryMapper;
    }

    /**
     * 解析"默认仓"：仅当启用的仓库**恰好一个**时返回该仓，否则返回 null。
     * <p>
     * 返回 null 的两种情况含义不同：
     * <ul>
     *   <li>0 个启用仓库：部署缺失，调用方应记日志提示运维配置仓库；</li>
     *   <li>≥2 个启用仓库：多仓模式，不应由系统替运营决定把货放在哪个仓。</li>
     * </ul>
     */
    public Warehouse resolveSingleWarehouse() {
        List<Warehouse> enabled = warehouseMapper.selectList(
            new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getStatus, 1));
        if (enabled == null || enabled.size() != 1) {
            return null;
        }
        return enabled.get(0);
    }

    /**
     * 确保某 SKU 存在库存记录；仅在单仓模式下、且该 SKU 尚无任何库存行时补建一行
     * （`stock = 0`，等运营填数量）。
     * <p>
     * <b>幂等</b>：已有任意库存行 → 不做任何改动。
     * <p>
     * <b>异常绝不外抛</b>：本方法的失败不得阻断商品上架——否则就是
     * "为了修一个小坑而制造一个更大的坑"（上架是核心业务，库存行可以事后补）。
     * 并发重复插入由 `uk_warehouse_sku(warehouse_id, sku_id)` 唯一键兜住，冲突忽略即可。
     *
     * @param skuId 新建的 SKU ID
     */
    public void ensureInventoryForSku(Long skuId) {
        if (skuId == null) {
            return;
        }
        try {
            List<Inventory> existing = inventoryMapper.selectList(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getSkuId, skuId));
            if (existing != null && !existing.isEmpty()) {
                return; // 幂等：已有库存行（任意仓）就不动
            }

            Warehouse defaultWarehouse = resolveSingleWarehouse();
            if (defaultWarehouse == null) {
                log.warn("未自动创建库存行：当前不是单仓模式（启用仓库数≠1 或未配置仓库）。"
                        + "请到「库存」页为 SKU {} 手工指定仓库与数量", skuId);
                return;
            }

            Inventory row = new Inventory();
            row.setWarehouseId(defaultWarehouse.getId());
            row.setSkuId(skuId);
            row.setStock(0);
            // warningStock / updateTime 交给数据库与自动填充，不在这里臆造
            row.setUpdateTime(LocalDateTime.now());
            inventoryMapper.insert(row);
            log.info("已为新建 SKU 自动创建库存记录: skuId={}, warehouseId={}（请到「库存」页填写数量）",
                skuId, defaultWarehouse.getId());
        } catch (Exception e) {
            // 有意吞掉：库存行可以事后补，上架不能因此失败。
            // 唯一键冲突（并发重复插入）也走这里，属预期情况。
            log.warn("自动创建库存记录失败（不影响上架，请到「库存」页手工补建）: skuId={}", skuId, e);
        }
    }
}
