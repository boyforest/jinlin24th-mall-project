package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.*;

/** 咖啡独立业务持久化数据。 */
@Data
@TableName("coffee_order_item")
public class CoffeeOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long productId;
    private Long skuId;
    private String productName;
    private String specName;
    private String imageUrl;
    private String temperature;
    private String sugar;
    private Long unitPriceCent;
    private Integer quantity;
    private Long lineTotalCent;
}
