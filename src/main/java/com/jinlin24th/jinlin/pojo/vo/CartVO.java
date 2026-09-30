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

    /**
     * 实际成交单价（后端按购买者身份算好）。
     * <p>
     * 前端**必须**用它显示与合计，不得自行在 price / memberPrice 之间判断——
     * 规则改为"仅分销会员享会员价"后，前端自行判断会导致"显示 A 价、结算 B 价"。
     */
    private BigDecimal effectivePrice;
}

