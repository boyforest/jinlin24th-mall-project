package com.jinlin24th.jinlin.common.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.mq.dto.OrderTimeoutCancelMessageDTO;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import com.jinlin24th.jinlin.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 订单超时取消消费者。
 * <p>
 * 延迟消息到达后，检查订单是否仍为待付款；只有待付款订单才会关闭并恢复库存。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${app.mq.topic-order-delay}",
    selectorExpression = "${app.mq.tag-order-timeout-cancel}",
    consumerGroup = "${app.mq.consumer-group-order-timeout}",
    maxReconsumeTimes = 3
)
public class OrderTimeoutCancelConsumer implements RocketMQListener<String> {

    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);
    private static final Duration BAD_MESSAGE_TTL = Duration.ofDays(7);

    private final RedisUtil redisUtil;
    private final OrderService orderService;

    public OrderTimeoutCancelConsumer(RedisUtil redisUtil, OrderService orderService) {
        this.redisUtil = redisUtil;
        this.orderService = orderService;
    }

    /**
     * 消费订单超时取消消息，业务失败时抛异常触发 MQ 重试。
     */
    @Override
    public void onMessage(String message) {
        OrderTimeoutCancelMessageDTO dto = parseMessage(message);
        if (dto == null) {
            return;
        }

        String consumedKey = "mq:consumed:order-timeout:" + dto.getOrderNo();
        if (!redisUtil.setIfAbsentRequired(consumedKey, "1", IDEMPOTENT_TTL)) {
            log.info("订单超时取消消息重复消费，已忽略: orderNo={}", dto.getOrderNo());
            return;
        }

        try {
            boolean cancelled = orderService.cancelUnpaidOrder(dto.getOrderNo(), "订单超时未支付，系统自动取消");
            log.info("订单超时取消处理完成: orderNo={}, cancelled={}", dto.getOrderNo(), cancelled);
        } catch (Exception e) {
            redisUtil.delete(consumedKey);
            log.error("订单超时取消消费失败，等待 RocketMQ 重试: orderNo={}", dto.getOrderNo(), e);
            throw e;
        }
    }

    /**
     * 解析订单延迟消息，坏消息暂存后不再重试。
     */
    private OrderTimeoutCancelMessageDTO parseMessage(String message) {
        try {
            OrderTimeoutCancelMessageDTO dto = JSON.parseObject(message, OrderTimeoutCancelMessageDTO.class);
            if (dto == null || dto.getOrderNo() == null || dto.getOrderNo().isBlank()) {
                saveBadMessage(message, "订单超时取消消息缺少 orderNo");
                return null;
            }
            return dto;
        } catch (Exception e) {
            saveBadMessage(message, "订单超时取消消息 JSON 解析失败");
            log.error("订单超时取消消息 JSON 解析失败", e);
            return null;
        }
    }

    /**
     * 保存无法解析或字段不完整的坏消息。
     */
    private void saveBadMessage(String message, String reason) {
        try {
            String key = "mq:bad:order-timeout:" + UUID.randomUUID();
            redisUtil.setRequired(key, reason + " | " + message, BAD_MESSAGE_TTL);
            log.warn("订单超时取消坏消息已暂存: key={}, reason={}", key, reason);
        } catch (Exception e) {
            log.error("订单超时取消坏消息暂存失败", e);
        }
    }
}
