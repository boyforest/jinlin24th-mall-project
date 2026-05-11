package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CartVO implements Serializable {
    private Long id;
    private Long userId;
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private Integer checked;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private String productName;
    private String productMainImage;
    private String skuName;
    private String skuImage;
    private BigDecimal price;
    private BigDecimal memberPrice;
    private Integer stock;
}

