package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CartDTO implements Serializable {
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private Integer checked;
}

