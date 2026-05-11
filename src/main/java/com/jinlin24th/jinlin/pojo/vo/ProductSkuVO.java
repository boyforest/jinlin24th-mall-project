package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductSkuVO implements Serializable {
    private Long id;
    private Long productId;
    private String skuName;
    private BigDecimal price;
    private BigDecimal memberPrice;
    private Integer stock;
    private String skuImage;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

