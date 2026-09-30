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

        // 幂等短路：已经处理成功的消息直接跳过。
        // 注意这里只用 exists 做快速判断，真正写标记放在业务成功之后——
        // 若先占坑再干活，JVM 在两者之间崩溃会让重投被误判为"重复消息"而丢弃，
        // 结果是订单永不取消、库存永不释放，且无处告警。
        if (redisUtil.exists(consumedKey)) {
            log.info("订单超时取消消息重复消费，已忽略: orderNo={}", dto.getOrderNo());
            return;
        }

        // 业务抛异常时不写标记，让 RocketMQ 正常重试
        boolean cancelled;
        try {
            cancelled = orderService.cancelUnpaidOrder(dto.getOrderNo(), "订单超时未支付，系统自动取消");
        } catch (Exception e) {
            log.error("订单超时取消消费失败，等待 RocketMQ 重试: orderNo={}", dto.getOrderNo(), e);
            throw e;
        }
        log.info("订单超时取消处理完成: orderNo={}, cancelled={}", dto.getOrderNo(), cancelled);

        // 业务已成功（cancelled=false 表示订单状态已变化、无需取消，同样算处理完成），
        // 此时才写幂等标记。
        redisUtil.setRequired(consumedKey, "1", IDEMPOTENT_TTL);
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
