package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CoffeeStoreVO {
    private String id;
    private String name;
    private String address;
    private String openingHours;
    private String timeZone;
    private String businessStatus;
    private String announcement;
    private Capabilities capabilities;
    private String orderUnavailableReason;

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Capabilities {
        private Boolean browse;
        private Boolean addToCart;
        private Boolean placeOrder;
        private Boolean payOnline;
    }
}
