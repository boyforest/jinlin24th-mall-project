package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.InventoryLog;
import com.jinlin24th.jinlin.mapper.InventoryLogMapper;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.entity.Warehouse;
import com.jinlin24th.jinlin.pojo.vo.InventoryLogVO;
import com.jinlin24th.jinlin.service.InventoryLogService;
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
public class InventoryLogServiceImpl extends ServiceImpl<InventoryLogMapper, InventoryLog>
    implements InventoryLogService {

    private final WarehouseService warehouseService;
    private final ProductSkuService productSkuService;

    public InventoryLogServiceImpl(WarehouseService warehouseService, ProductSkuService productSkuService) {
        this.warehouseService = warehouseService;
        this.productSkuService = productSkuService;
    }

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
        voPage.setRecords(toVOList(entityPage.getRecords()));
        return voPage;
    }

    @Override
    public InventoryLogVO getVO(Long id) {
        InventoryLog log = getById(id);
        return log == null ? null : toVOList(Collections.singletonList(log)).get(0);
    }

    private java.util.List<InventoryLogVO> toVOList(java.util.List<InventoryLog> records) {
        Set<Long> warehouseIds = records.stream().map(InventoryLog::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> skuIds = records.stream().map(InventoryLog::getSkuId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> warehouseNames = warehouseIds.isEmpty()
            ? Collections.emptyMap()
            : warehouseService.listByIds(warehouseIds).stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getName, (a, b) -> a));
        Map<Long, String> skuNames = skuIds.isEmpty()
            ? Collections.emptyMap()
            : productSkuService.listByIds(skuIds).stream().collect(Collectors.toMap(ProductSku::getId, ProductSku::getSkuName, (a, b) -> a));

        return records.stream().map(e -> {
            InventoryLogVO vo = new InventoryLogVO();
            BeanUtils.copyProperties(e, vo);
            vo.setWarehouseName(warehouseNames.get(e.getWarehouseId()));
            vo.setSkuName(skuNames.get(e.getSkuId()));
            return vo;
        }).collect(Collectors.toList());
    }
}

