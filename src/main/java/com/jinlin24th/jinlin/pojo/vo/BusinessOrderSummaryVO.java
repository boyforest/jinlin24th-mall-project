package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 只返回用户展示所需快照，不暴露地址、手机号或推荐官资料。 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class BusinessOrderSummaryVO {
    private String businessType;
    private String id;
    private String orderNo;
    private String storeId;
    private String storeName;
    private String fulfillmentType;
    private String status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime createdAt;

    private Long itemCount;
    private List<Item> summaryItems;
    private Amounts amounts;
    private String pickupCode;
    private String pickupDate;

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Item {
        private String productId;
        private String skuId;
        private String productName;
        private String specName;
        private String imageUrl;
        private Long unitPriceCent;
        private Integer quantity;
        private Long lineTotalCent;
    }

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Amounts {
        private Long totalAmountCent;
        private Long discountAmountCent;
        private Long freightAmountCent;
        private Long payableAmountCent;
        private Long paidAmountCent;
    }
}
