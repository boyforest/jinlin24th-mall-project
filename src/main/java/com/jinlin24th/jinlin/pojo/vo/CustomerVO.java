package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CustomerVO implements Serializable {
    private Long id;
    private String name;
    private String contactName;
    private String contactPhone;
    private Integer source;
    private Integer level;
    private Long adminId;
    private String adminName;
    private BigDecimal totalAmount;
    private Integer orderCount;
    private String tags;
    private String remark;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
