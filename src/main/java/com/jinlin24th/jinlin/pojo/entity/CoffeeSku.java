package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.LocalDateTime;

/** 咖啡独立业务数据，不参与商品库存、支付和分销。 */
@Data
@TableName("coffee_sku")
public class CoffeeSku {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;
    private Long productId;
    private String name;
    private String temperature;
    private String sugar;
    private Long priceCent;
    private Integer status;
    private Boolean available;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
