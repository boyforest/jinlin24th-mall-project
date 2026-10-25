package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.*;

/** 咖啡独立业务持久化数据。 */
@Data
@TableName("coffee_refund")
public class CoffeeRefund {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String refundNo;
    private String requestKey;
    private String requestHash;
    private String reason;
    private Long operatorId;
    private Long amountCent;
    private String status;
    private String channelRefundId;
    private Boolean submitted;
    private LocalDateTime createdAt;
    private LocalDateTime succeededAt;
    private LocalDateTime nextQueryAt;
    private String lastError;
}
