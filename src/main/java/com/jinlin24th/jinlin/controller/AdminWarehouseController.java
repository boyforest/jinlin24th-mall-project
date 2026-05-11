package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.entity.Warehouse;
import com.jinlin24th.jinlin.pojo.vo.WarehouseVO;
import com.jinlin24th.jinlin.service.WarehouseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/warehouse")
public class AdminWarehouseController {

    @Autowired
    private WarehouseService warehouseService;

    @GetMapping("/list")
    public Result<IPage<WarehouseVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Integer status
    ) {
        return Result.success(warehouseService.adminPage(page, size, status));
    }

    @GetMapping("/{id}")
    public Result<Warehouse> get(@PathVariable Long id) {
        Warehouse warehouse = warehouseService.getRequired(id);
        if (warehouse == null) {
            return Result.error(404, "仓库不存在");
        }
        return Result.success(warehouse);
    }

    @PostMapping
    public Result<Warehouse> create(@RequestBody Warehouse warehouse) {
        return Result.success(warehouseService.create(warehouse));
    }

    @PutMapping("/{id}")
    public Result<Warehouse> update(@PathVariable Long id, @RequestBody Warehouse warehouse) {
        Warehouse updated = warehouseService.update(id, warehouse);
        if (updated == null) {
            return Result.error(404, "仓库不存在");
        }
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(warehouseService.delete(id));
    }
}
