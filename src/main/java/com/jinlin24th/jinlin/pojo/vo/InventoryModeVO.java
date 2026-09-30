package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 库存管理模式（供管理端决定是否隐藏"仓库"这一层）。
 * <p>
 * 「单仓模式」= 启用仓库恰好一个。此时库存页隐藏仓库筛选/列/表单字段，
 * 运营只需面向 SKU 维护数量，不必理解仓库维度。
 * <p>
 * 多仓（或未配置仓库）时 {@code singleWarehouse=false}，前端必须回退到显式选择仓库，
 * 绝不能由系统替运营猜测仓位。
 */
@Data
public class InventoryModeVO implements Serializable {

    /** 是否单仓模式（启用仓库恰好一个） */
    private Boolean singleWarehouse;

    /** 单仓模式下的默认仓 ID；非单仓模式为 null */
    private Long defaultWarehouseId;

    /** 单仓模式下的默认仓名称；非单仓模式为 null */
    private String defaultWarehouseName;
}
