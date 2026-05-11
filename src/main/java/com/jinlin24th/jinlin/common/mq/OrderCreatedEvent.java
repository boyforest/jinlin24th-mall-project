package com.jinlin24th.jinlin.common.mq;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderCreatedEvent implements Serializable {
    private Long orderId;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private String createTime;
}
