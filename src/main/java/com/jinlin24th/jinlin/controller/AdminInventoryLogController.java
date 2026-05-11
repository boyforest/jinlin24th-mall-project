package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.entity.InventoryLog;
import com.jinlin24th.jinlin.pojo.vo.InventoryLogVO;
import com.jinlin24th.jinlin.service.InventoryLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/inventory/log")
public class AdminInventoryLogController {

    @Autowired
    private InventoryLogService inventoryLogService;

    @GetMapping("/list")
    public Result<IPage<InventoryLogVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Long warehouseId,
        @RequestParam(required = false) Long skuId,
        @RequestParam(required = false) Integer type
    ) {
        return Result.success(inventoryLogService.adminPage(page, size, warehouseId, skuId, type));
    }

    @GetMapping("/{id}")
    public Result<InventoryLog> get(@PathVariable Long id) {
        InventoryLog log = inventoryLogService.getRequired(id);
        if (log == null) {
            return Result.error(404, "库存流水不存在");
        }
        return Result.success(log);
    }
}
