package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponVO implements Serializable {
    private Long id;
    private String name;
    private Integer type;
    private BigDecimal minAmount;
    private BigDecimal discountValue;
    private Integer stock;
    private Integer receivedCount;
    private Integer usedCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long memberLevelId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

