package com.jinlin24th.jinlin.common.mq;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${app.mq.topic-order-created}",
    consumerGroup = "${app.mq.consumer-group-order-created}"
)
public class OrderCreatedMqConsumer implements RocketMQListener<String> {

    private static final Duration PROCESSING_TTL = Duration.ofMinutes(10);
    private static final Duration CONSUMED_TTL = Duration.ofDays(7);
    private static final Duration BAD_MSG_TTL = Duration.ofDays(7);

    private final RedisUtil redisUtil;

    public OrderCreatedMqConsumer(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    @Override
    public void onMessage(String message) {
        OrderCreatedEvent event;
        try {
            event = JSON.parseObject(message, OrderCreatedEvent.class);
        } catch (Exception e) {
            String key = "mq:bad:order-created:" + UUID.randomUUID();
            redisUtil.setRequired(key, message, BAD_MSG_TTL);
            log.error("RocketMQ 订单创建消息反序列化失败: key={}", key, e);
            return;
        }

        if (event == null || event.getOrderNo() == null || event.getOrderNo().isBlank()) {
            String key = "mq:bad:order-created:" + UUID.randomUUID();
            redisUtil.setRequired(key, message, BAD_MSG_TTL);
            log.error("RocketMQ 订单创建消息缺少 orderNo: key={}", key);
            return;
        }

        String consumedKey = "mq:consumed:order-created:" + event.getOrderNo();
        if (redisUtil.exists(consumedKey)) {
            log.info("RocketMQ 重复订单创建消息已忽略: orderNo={}", event.getOrderNo());
            return;
        }

        String processingKey = "mq:processing:order-created:" + event.getOrderNo();
        boolean acquired = redisUtil.setIfAbsentRequired(processingKey, "1", PROCESSING_TTL);
        if (!acquired) {
            log.info("RocketMQ 订单创建消息正在处理中，暂时忽略重复投递: orderNo={}", event.getOrderNo());
            return;
        }

        try {
            log.info("RocketMQ 消费到订单创建消息: orderNo={}, userId={}, amount={}",
                event.getOrderNo(), event.getUserId(), event.getTotalAmount());
            redisUtil.setRequired(consumedKey, "1", CONSUMED_TTL);
        } finally {
            redisUtil.delete(processingKey);
        }
    }
}
