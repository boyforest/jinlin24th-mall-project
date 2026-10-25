package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.LocalDateTime;

/** 咖啡独立业务数据，不参与商品库存、支付和分销。 */
@Data
@TableName("coffee_cart")
public class CoffeeCart {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long storeId;
    private Long skuId;
    private Integer quantity;
    private Integer checked;
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
