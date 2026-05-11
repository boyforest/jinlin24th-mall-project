package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserCouponVO implements Serializable {
    private Long id;
    private Long userId;
    private Long couponId;
    private Long orderId;
    private Integer status;
    private LocalDateTime receiveTime;
    private LocalDateTime useTime;

    private String name;
    private Integer type;
    private BigDecimal minAmount;
    private BigDecimal discountValue;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer couponStatus;
}

