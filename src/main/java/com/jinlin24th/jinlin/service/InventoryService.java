package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.vo.InventoryVO;

public interface InventoryService extends IService<Inventory> {
    IPage<InventoryVO> adminPage(long page, long size, Long warehouseId, Long skuId);

    InventoryVO getVO(Long id);

    Inventory update(Long id, Inventory inventory);

    /**
     * 原子归还库存（数据库端 {@code stock = stock + quantity}）。
     * <p>
     * 用于订单取消/退款时归还下单占用的库存。实现必须走单条原子 SQL，
     * 不做"读改写 + CAS + 重试"，否则并发下会丢失更新或静默丢库存。
     *
     * @return 影响行数（0 表示库存记录不存在，调用方应视为失败并回滚事务）
     */
    int incrementStock(Long id, int quantity);
}

