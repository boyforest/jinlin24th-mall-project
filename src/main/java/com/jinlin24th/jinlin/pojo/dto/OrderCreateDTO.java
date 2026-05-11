package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OrderCreateDTO implements Serializable {
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private List<Item> items;

    @Data
    public static class Item implements Serializable {
        private Long skuId;
        private Integer quantity;
    }
}

