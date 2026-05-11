package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 微信支付下单请求DTO
 */
@Data
public class WxPayPrepayDTO {

    /**
     * 应用ID
     */
    @NotBlank(message = "应用ID不能为空")
    private String appid;

    /**
     * 商户订单号
     */
    @NotBlank(message = "商户订单号不能为空")
    private String outTradeNo;

    /**
     * 订单描述
     */
    @NotBlank(message = "订单描述不能为空")
    private String description;

    /**
     * 订单金额(元)
     */
    @NotNull(message = "订单金额不能为空")
    private BigDecimal totalAmount;

    /**
     * 用户openid
     */
    @NotBlank(message = "用户openid不能为空")
    private String openid;

    /**
     * 订单类型
     */
    private String orderType;
}
