package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.vo.InventoryVO;
import com.jinlin24th.jinlin.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/inventory")
public class AdminInventoryController {

    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/list")
    public Result<IPage<InventoryVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Long warehouseId,
        @RequestParam(required = false) Long skuId
    ) {
        return Result.success(inventoryService.adminPage(page, size, warehouseId, skuId));
    }

    @GetMapping("/{id}")
    public Result<Inventory> get(@PathVariable Long id) {
        Inventory inventory = inventoryService.getRequired(id);
        if (inventory == null) {
            return Result.error(404, "库存记录不存在");
        }
        return Result.success(inventory);
    }

    @PutMapping("/{id}")
    public Result<Inventory> update(@PathVariable Long id, @RequestBody Inventory inventory) {
        Inventory updated = inventoryService.update(id, inventory);
        if (updated == null) {
            return Result.error(404, "库存记录不存在");
        }
        return Result.success(updated);
    }
}
