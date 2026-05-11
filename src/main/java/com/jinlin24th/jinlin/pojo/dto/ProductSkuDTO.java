package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ProductSkuDTO implements Serializable {
    private Long productId;
    private String skuName;
    private BigDecimal price;
    private BigDecimal memberPrice;
    private Integer stock;
    private String skuImage;
    private Integer status;
}

