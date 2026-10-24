package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.util.List;

/** 只读报价：不创建订单、不占用库存，提交时仍由服务端重新校验。 */
@Data
public class OrderPreviewVO {
    private List<Item> items;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal payAmount;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Item extends OrderVO.Item {
        private Integer stock;
    }
}
