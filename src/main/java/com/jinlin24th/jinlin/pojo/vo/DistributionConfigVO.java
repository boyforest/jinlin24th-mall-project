package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DistributionConfigVO implements Serializable {
    private Long id;
    private Integer level1Rate;
    private Integer level2Rate;
    private BigDecimal minWithdraw;
    private Integer settleDays;
    private Integer status;
    private LocalDateTime updateTime;
}

