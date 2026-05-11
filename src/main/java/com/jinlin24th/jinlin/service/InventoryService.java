package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.vo.InventoryVO;

public interface InventoryService extends IService<Inventory> {
    IPage<InventoryVO> adminPage(long page, long size, Long warehouseId, Long skuId);

    Inventory getRequired(Long id);

    Inventory update(Long id, Inventory inventory);
}


