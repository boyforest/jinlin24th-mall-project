package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.mapper.InventoryMapper;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.entity.Warehouse;
import com.jinlin24th.jinlin.pojo.vo.InventoryVO;
import com.jinlin24th.jinlin.service.InventoryService;
import com.jinlin24th.jinlin.service.ProductSkuService;
import com.jinlin24th.jinlin.service.WarehouseService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory>
    implements InventoryService {

    private final WarehouseService warehouseService;
    private final ProductSkuService productSkuService;

    public InventoryServiceImpl(WarehouseService warehouseService, ProductSkuService productSkuService) {
        this.warehouseService = warehouseService;
        this.productSkuService = productSkuService;
    }

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
        voPage.setRecords(toVOList(entityPage.getRecords()));
        return voPage;
    }

    @Override
    public InventoryVO getVO(Long id) {
        Inventory inventory = getById(id);
        return inventory == null ? null : toVOList(Collections.singletonList(inventory)).get(0);
    }

    @Override
    public Inventory update(Long id, Inventory inventory) {
        // 按 id 更新：以 path 里的 id 为准，避免前端乱传 id
        inventory.setId(id);
        updateById(inventory);
        return getById(id);
    }

    private java.util.List<InventoryVO> toVOList(java.util.List<Inventory> records) {
        Set<Long> warehouseIds = records.stream().map(Inventory::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> skuIds = records.stream().map(Inventory::getSkuId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> warehouseNames = warehouseIds.isEmpty()
            ? Collections.emptyMap()
            : warehouseService.listByIds(warehouseIds).stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getName, (a, b) -> a));
        Map<Long, String> skuNames = skuIds.isEmpty()
            ? Collections.emptyMap()
            : productSkuService.listByIds(skuIds).stream().collect(Collectors.toMap(ProductSku::getId, ProductSku::getSkuName, (a, b) -> a));

        return records.stream().map(e -> {
            InventoryVO vo = new InventoryVO();
            BeanUtils.copyProperties(e, vo);
            vo.setWarehouseName(warehouseNames.get(e.getWarehouseId()));
            vo.setSkuName(skuNames.get(e.getSkuId()));
            return vo;
        }).collect(Collectors.toList());
    }
}
