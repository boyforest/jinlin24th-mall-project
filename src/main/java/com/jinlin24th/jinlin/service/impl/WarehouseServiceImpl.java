package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.Warehouse;
import com.jinlin24th.jinlin.mapper.WarehouseMapper;
import com.jinlin24th.jinlin.pojo.vo.WarehouseVO;
import com.jinlin24th.jinlin.service.WarehouseService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class WarehouseServiceImpl extends ServiceImpl<WarehouseMapper, Warehouse>
    implements WarehouseService {

    @Override
    public IPage<WarehouseVO> adminPage(long page, long size, Integer status) {
        Page<Warehouse> p = new Page<>(page, size);
        IPage<Warehouse> entityPage = lambdaQuery()
            .eq(status != null, Warehouse::getStatus, status)
            .orderByDesc(Warehouse::getId)
            .page(p);
        Page<WarehouseVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            WarehouseVO vo = new WarehouseVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Warehouse getRequired(Long id) {
        return getById(id);
    }

    @Override
    public Warehouse create(Warehouse warehouse) {
        save(warehouse);
        return warehouse;
    }

    @Override
    public Warehouse update(Long id, Warehouse warehouse) {
        warehouse.setId(id);
        updateById(warehouse);
        return getById(id);
    }

    @Override
    public Boolean delete(Long id) {
        return removeById(id);
    }
}

