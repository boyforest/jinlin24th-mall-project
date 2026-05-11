package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.entity.InventoryLog;
import com.jinlin24th.jinlin.pojo.vo.InventoryLogVO;

public interface InventoryLogService extends IService<InventoryLog> {
    IPage<InventoryLogVO> adminPage(long page, long size, Long warehouseId, Long skuId, Integer type);

    InventoryLog getRequired(Long id);
}


