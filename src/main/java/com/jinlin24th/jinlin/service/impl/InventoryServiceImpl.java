package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.mapper.InventoryMapper;
import com.jinlin24th.jinlin.pojo.vo.InventoryVO;
import com.jinlin24th.jinlin.service.InventoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory>
    implements InventoryService {

    @Override
    public IPage<InventoryVO> adminPage(long page, long size, Long warehouseId, Long skuId) {
        // 管理端库存分页：支持按仓库/sku 筛选
        Page<Inventory> p = new Page<>(page, size);
        IPage<Inventory> entityPage = lambdaQuery()
            .eq(warehouseId != null, Inventory::getWarehouseId, warehouseId)
            .eq(skuId != null, Inventory::getSkuId, skuId)
            .orderByDesc(Inventory::getId)
            .page(p);
        Page<InventoryVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            InventoryVO vo = new InventoryVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Inventory getRequired(Long id) {
        // 简化实现：直接查询；后续可改为“不存在抛异常”
        return getById(id);
    }

    @Override
    public Inventory update(Long id, Inventory inventory) {
        // 按 id 更新：以 path 里的 id 为准，避免前端乱传 id
        inventory.setId(id);
        updateById(inventory);
        return getById(id);
    }
}
