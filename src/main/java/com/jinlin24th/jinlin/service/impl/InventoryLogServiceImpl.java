package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.InventoryLog;
import com.jinlin24th.jinlin.mapper.InventoryLogMapper;
import com.jinlin24th.jinlin.pojo.vo.InventoryLogVO;
import com.jinlin24th.jinlin.service.InventoryLogService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class InventoryLogServiceImpl extends ServiceImpl<InventoryLogMapper, InventoryLog>
    implements InventoryLogService {

    @Override
    public IPage<InventoryLogVO> adminPage(long page, long size, Long warehouseId, Long skuId, Integer type) {
        Page<InventoryLog> p = new Page<>(page, size);
        IPage<InventoryLog> entityPage = lambdaQuery()
            .eq(warehouseId != null, InventoryLog::getWarehouseId, warehouseId)
            .eq(skuId != null, InventoryLog::getSkuId, skuId)
            .eq(type != null, InventoryLog::getType, type)
            .orderByDesc(InventoryLog::getId)
            .page(p);
        Page<InventoryLogVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            InventoryLogVO vo = new InventoryLogVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public InventoryLog getRequired(Long id) {
        return getById(id);
    }
}


