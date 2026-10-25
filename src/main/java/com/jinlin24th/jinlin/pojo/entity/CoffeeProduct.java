package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.LocalDateTime;

/** 咖啡独立业务数据，不参与商品库存、支付和分销。 */
@Data
@TableName("coffee_product")
public class CoffeeProduct {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;
    private Long categoryId;
    private String name;
    private String imageUrl;
    private String description;
    private Integer sort;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
