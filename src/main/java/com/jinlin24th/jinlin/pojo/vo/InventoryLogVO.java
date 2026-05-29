package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class InventoryLogVO implements Serializable {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long skuId;
    private String skuName;
    private Integer type;
    private Integer quantity;
    private Integer beforeStock;
    private Integer afterStock;
    private String orderNo;
    private String remark;
    private Long operatorId;
    private LocalDateTime createTime;
}

