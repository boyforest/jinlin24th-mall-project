package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水�? * 对应数据库表：inventory_log
 */
@Data
@TableName("inventory_log")
public class InventoryLog {

    /**
     * 流水ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 类型�?-入库�?-出库�?-盘点调整
     */
    private Integer type;

    /**
     * 变动数量（正=入，�?出）
     */
    private Integer quantity;

    /**
     * 变动前库�?     */
    private Integer beforeStock;

    /**
     * 变动后库�?     */
    private Integer afterStock;

    /**
     * 关联订单�?     */
    private String orderNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

