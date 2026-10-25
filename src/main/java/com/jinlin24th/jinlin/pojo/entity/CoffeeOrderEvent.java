package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.*;

/** 咖啡独立业务持久化数据。 */
@Data
@TableName("coffee_order_event")
public class CoffeeOrderEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String reason;
    private LocalDateTime createdAt;
}
