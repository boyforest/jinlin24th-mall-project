package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CoffeeCartVO {
    private String id;
    private String storeId;
    private String productId;
    private String skuId;
    private String productName;
    private String skuName;
    private String imageUrl;
    private String temperature;
    private String sugar;
    private Long unitPriceCent;
    private Integer quantity;
    private Integer checked;
    private Boolean available;
    private String unavailableReason;
    private Long version;
}
