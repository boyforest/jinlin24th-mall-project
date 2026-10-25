package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.*;

/** 咖啡独立业务持久化数据。 */
@Data
@TableName("coffee_pickup_sequence")
public class CoffeePickupSequence {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;
    private LocalDate pickupDate;
    private Long nextNumber;
}
