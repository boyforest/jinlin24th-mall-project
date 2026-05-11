package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class DistributionConfigDTO implements Serializable {
    private Integer level1Rate;
    private Integer level2Rate;
    private BigDecimal minWithdraw;
    private Integer settleDays;
    private Integer status;
}

