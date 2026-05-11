package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DistributionVO implements Serializable {
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long buyerUserId;
    private BigDecimal buyerAmount;
    private Long level1UserId;
    private Integer level1Rate;
    private BigDecimal level1Amount;
    private Long level2UserId;
    private Integer level2Rate;
    private BigDecimal level2Amount;
    private Integer status;
    private LocalDateTime settleTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

