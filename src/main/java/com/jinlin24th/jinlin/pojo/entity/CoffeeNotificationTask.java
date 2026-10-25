package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.*;

/** 咖啡独立业务持久化数据。 */
@Data
@TableName("coffee_notification_task")
public class CoffeeNotificationTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long readyEventId;
    private String templateType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String reason;
}
