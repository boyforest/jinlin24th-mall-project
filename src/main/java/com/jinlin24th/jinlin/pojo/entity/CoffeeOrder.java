package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.*;

/** 咖啡独立业务持久化数据。 */
@Data
@TableName("coffee_order")
public class CoffeeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long storeId;
    private String storeName;
    private String requestKey;
    private String requestHash;
    private String pickupCode;
    private LocalDate pickupDate;
    private String fulfillmentStatus;
    private String paymentStatus;
    private String refundStatus;
    private String refundReason;
    private String remark;
    private Integer itemCount;
    private Long totalAmountCent;
    private Long payableAmountCent;
    private Long paidAmountCent;
    private Long refundedAmountCent;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime makingAt;
    private LocalDateTime readyAt;
    private LocalDateTime completedAt;
    private LocalDateTime stageEnteredAt;
}
