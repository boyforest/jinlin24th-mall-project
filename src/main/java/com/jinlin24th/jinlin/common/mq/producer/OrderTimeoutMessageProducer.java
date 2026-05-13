package com.jinlin24th.jinlin.common.mq.producer;

import com.jinlin24th.jinlin.common.config.RocketMQConfig;
import com.jinlin24th.jinlin.common.mq.dto.OrderTimeoutCancelMessageDTO;
import com.jinlin24th.jinlin.common.mq.support.RocketMQSendHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单超时取消消息生产者。
 * <p>
 * 下单成功后发送延迟消息，到期后由消费者决定订单是否需要关闭。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class OrderTimeoutMessageProducer {

    private final RocketMQConfig rocketMQConfig;
    private final RocketMQSendHelper rocketMQSendHelper;

    public OrderTimeoutMessageProducer(RocketMQConfig rocketMQConfig, RocketMQSendHelper rocketMQSendHelper) {
        this.rocketMQConfig = rocketMQConfig;
        this.rocketMQSendHelper = rocketMQSendHelper;
    }

    /**
     * 发送订单 30 分钟超时取消消息。
     */
    public boolean sendTimeoutCancel(Long orderId, String orderNo, Long userId) {
        long delaySeconds = rocketMQConfig.getOrderTimeoutCancelDelaySeconds();
        LocalDateTime now = LocalDateTime.now();

        OrderTimeoutCancelMessageDTO message = new OrderTimeoutCancelMessageDTO();
        message.setMessageKey("order:timeout:" + orderNo);
        message.setOrderId(orderId);
        message.setOrderNo(orderNo);
        message.setUserId(userId);
        message.setCreateTime(now);
        message.setExpireTime(now.plusSeconds(delaySeconds));

        return rocketMQSendHelper.syncSendDelayWithRetry(
            rocketMQConfig.getTopicOrderDelay(),
            rocketMQConfig.getTagOrderTimeoutCancel(),
            message.getMessageKey(),
            message,
            delaySeconds
        );
    }
}
