package com.jinlin24th.jinlin.common.mq;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class RocketMQOrderCreatedDispatcher implements OrderCreatedDispatcher {

    private static final Duration FAILED_EVENT_TTL = Duration.ofDays(7);

    private final RocketMQTemplate rocketMQTemplate;
    private final RedisUtil redisUtil;
    private final String topic;

    public RocketMQOrderCreatedDispatcher(
        RocketMQTemplate rocketMQTemplate,
        RedisUtil redisUtil,
        @Value("${app.mq.topic-order-created}") String topic
    ) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.redisUtil = redisUtil;
        this.topic = topic;
    }

    @Override
    public void dispatch(OrderCreatedEvent event) {
        String payload = JSON.toJSONString(event);
        Message<String> message = MessageBuilder.withPayload(payload)
            .setHeader(RocketMQHeaders.KEYS, event.getOrderNo())
            .build();
        try {
            SendResult result = rocketMQTemplate.syncSend(topic, message);
            log.info("RocketMQ 已发送订单创建消息: orderNo={}, msgId={}, status={}",
                event.getOrderNo(), result.getMsgId(), result.getSendStatus());
        } catch (Exception e) {
            log.error("RocketMQ 发送订单创建消息失败: orderNo={}", event.getOrderNo(), e);
            redisUtil.setRequired("mq:failed:order-created:" + event.getOrderNo(), payload, FAILED_EVENT_TTL);
        }
    }
}
