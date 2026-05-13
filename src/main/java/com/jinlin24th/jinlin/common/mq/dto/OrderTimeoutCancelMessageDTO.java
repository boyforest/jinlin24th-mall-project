package com.jinlin24th.jinlin.common.mq.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单超时取消消息 DTO。
 * <p>
 * 下单成功后发送延迟消息，延迟到期时消费者检查订单是否仍未支付，若未支付则自动关闭并恢复库存。
 */
@Data
public class OrderTimeoutCancelMessageDTO implements Serializable {

    /**
     * 消息唯一键，用于生产排查和消费幂等。
     */
    private String messageKey;

    /**
     * 订单主键 ID。
     */
    private Long orderId;

    /**
     * 订单编号。
     */
    private String orderNo;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 订单创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 预计关闭时间。
     */
    private LocalDateTime expireTime;
}
