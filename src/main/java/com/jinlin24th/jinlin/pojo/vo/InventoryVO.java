package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class InventoryVO implements Serializable {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long skuId;
    private String skuName;
    private Integer stock;
    private Integer warningStock;
    private LocalDateTime updateTime;
}

