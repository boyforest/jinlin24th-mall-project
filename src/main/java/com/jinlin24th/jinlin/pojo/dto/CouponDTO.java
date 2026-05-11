package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponDTO implements Serializable {
    private String name;
    private Integer type;
    private BigDecimal minAmount;
    private BigDecimal discountValue;
    private Integer stock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long memberLevelId;
    private Integer status;
}

