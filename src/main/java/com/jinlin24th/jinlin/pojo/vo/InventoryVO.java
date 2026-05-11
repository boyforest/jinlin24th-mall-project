package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class InventoryVO implements Serializable {
    private Long id;
    private Long warehouseId;
    private Long skuId;
    private Integer stock;
    private Integer warningStock;
    private LocalDateTime updateTime;
}

