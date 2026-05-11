package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.entity.Warehouse;
import com.jinlin24th.jinlin.pojo.vo.WarehouseVO;

public interface WarehouseService extends IService<Warehouse> {
    IPage<WarehouseVO> adminPage(long page, long size, Integer status);

    Warehouse getRequired(Long id);

    Warehouse create(Warehouse warehouse);

    Warehouse update(Long id, Warehouse warehouse);

    Boolean delete(Long id);
}


