package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.vo.InventoryVO;
import com.jinlin24th.jinlin.pojo.entity.Warehouse;
import com.jinlin24th.jinlin.pojo.vo.InventoryModeVO;
import com.jinlin24th.jinlin.service.InventoryMaintenanceService;
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

    @Autowired
    private InventoryMaintenanceService inventoryMaintenanceService;

    /**
     * 查询库存管理模式。前端据此决定「库存」页是否隐藏"仓库"维度。
     * <p>
     * 单仓模式 = 启用仓库恰好一个（派生判定，不引入配置项）：
     * 将来增加第二个仓库时，界面会自动恢复"选择仓库"，不会留下忘记关闭的开关。
     */
    @GetMapping("/mode")
    public Result<InventoryModeVO> mode() {
        Warehouse single = inventoryMaintenanceService.resolveSingleWarehouse();
        InventoryModeVO vo = new InventoryModeVO();
        vo.setSingleWarehouse(single != null);
        vo.setDefaultWarehouseId(single == null ? null : single.getId());
        vo.setDefaultWarehouseName(single == null ? null : single.getName());
        return Result.success(vo);
    }

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
    public Result<InventoryVO> get(@PathVariable Long id) {
        InventoryVO inventory = inventoryService.getVO(id);
        if (inventory == null) {
            throw BizException.of(BizCode.INVENTORY_NOT_FOUND);
        }
        return Result.success(inventory);
    }

    @PutMapping("/{id}")
    public Result<Inventory> update(@PathVariable Long id, @RequestBody Inventory inventory) {
        Inventory updated = inventoryService.update(id, inventory);
        if (updated == null) {
            throw BizException.of(BizCode.INVENTORY_NOT_FOUND);
        }
        return Result.success(updated);
    }
}
