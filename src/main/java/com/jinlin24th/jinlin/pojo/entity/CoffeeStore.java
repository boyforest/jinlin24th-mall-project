package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.LocalDateTime;

/** 咖啡独立业务数据，不参与商品库存、支付和分销。 */
@Data
@TableName("coffee_store")
public class CoffeeStore {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long primarySlot;
    private String name;
    private String address;
    private String openingHours;
    private String timeZone;
    private String businessStatus;
    private String announcement;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
