package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CoffeeOrderVO {
    private String id;
    private String orderNo;
    private String storeId;
    private String storeName;
    private String pickupCode;
    private String pickupDate;
    private String fulfillmentStatus;
    private String paymentStatus;
    private String refundStatus;
    private String refundReason;
    private String createdAt;
    private String paidAt;
    private String acceptedAt;
    private String makingAt;
    private String readyAt;
    private String completedAt;
    private String stageEnteredAt;
    private Long version;
    private String remark;
    private Integer itemCount;
    private List<Item> items;
    private Amounts amounts;
    private List<String> allowedActions;
    private String notificationStatus;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Event> events;

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Item {
        private String productId;
        private String skuId;
        private String productName;
        private String specName;
        private String imageUrl;
        private String temperature;
        private String sugar;
        private Long unitPriceCent;
        private Integer quantity;
        private Long lineTotalCent;
    }

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Amounts {
        private Long totalAmountCent;
        private Long payableAmountCent;
        private Long paidAmountCent;
        private Long refundedAmountCent;
    }

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Event {
        private String action;
        private String operatorName;
        private String createdAt;
        private String reason;
    }
}
