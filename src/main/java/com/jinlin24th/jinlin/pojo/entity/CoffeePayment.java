package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.*;

/** 咖啡独立业务持久化数据。 */
@Data
@TableName("coffee_payment")
public class CoffeePayment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String merchantOrderNo;
    private String merchantId;
    private String appId;
    private String transactionId;
    private Long amountCent;
    private String status;
    private Boolean initiated;
    private Boolean closeRequested;
    private String prepayId;
    private LocalDateTime paidAt;
    private LocalDateTime nextQueryAt;
    private String lastError;
}
