package com.jinlin24th.jinlin.common.mq.support;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.config.RocketMQConfig;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RocketMQ 发送工具。
 * <p>
 * 统一处理 JSON 消息体、消息 key、发送重试和失败消息暂存，避免每个生产者重复写样板代码。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class RocketMQSendHelper {

    private static final Duration FAILED_MESSAGE_TTL = Duration.ofDays(7);

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQConfig rocketMQConfig;
    private final RedisUtil redisUtil;

    public RocketMQSendHelper(RocketMQTemplate rocketMQTemplate, RocketMQConfig rocketMQConfig, RedisUtil redisUtil) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.rocketMQConfig = rocketMQConfig;
        this.redisUtil = redisUtil;
    }

    /**
     * 同步发送普通消息，失败后按配置主动重试。
     *
     * @param topic topic 名称
     * @param tag tag 名称
     * @param key 消息 key
     * @param body 消息 DTO
     * @return 是否发送成功
     */
    public boolean syncSendWithRetry(String topic, String tag, String key, Object body) {
        String destination = destination(topic, tag);
        String payload = JSON.toJSONString(body);
        Message<String> message = buildMessage(key, payload);
        int retryTimes = Math.max(1, rocketMQConfig.getProducerRetryTimes());

        for (int i = 1; i <= retryTimes; i++) {
            try {
                SendResult result = rocketMQTemplate.syncSend(destination, message, rocketMQConfig.getSendTimeoutMs());
                log.info("RocketMQ 消息发送成功: destination={}, key={}, msgId={}, status={}, attempt={}",
                    destination, key, result.getMsgId(), result.getSendStatus(), i);
                return true;
            } catch (Exception e) {
                log.error("RocketMQ 消息发送失败: destination={}, key={}, attempt={}/{}",
                    destination, key, i, retryTimes, e);
            }
        }

        saveFailedMessage(destination, key, payload);
        return false;
    }

    /**
     * 同步发送延迟消息，适合订单超时取消。
     *
     * @param delaySeconds 延迟秒数
     */
    public boolean syncSendDelayWithRetry(String topic, String tag, String key, Object body, long delaySeconds) {
        String destination = destination(topic, tag);
        String payload = JSON.toJSONString(body);
        Message<String> message = buildMessage(key, payload);
        int retryTimes = Math.max(1, rocketMQConfig.getProducerRetryTimes());

        for (int i = 1; i <= retryTimes; i++) {
            try {
                SendResult result = rocketMQTemplate.syncSendDelayTimeSeconds(destination, message, delaySeconds);
                log.info("RocketMQ 延迟消息发送成功: destination={}, key={}, delaySeconds={}, msgId={}, status={}, attempt={}",
                    destination, key, delaySeconds, result.getMsgId(), result.getSendStatus(), i);
                return true;
            } catch (Exception e) {
                log.error("RocketMQ 延迟消息发送失败: destination={}, key={}, delaySeconds={}, attempt={}/{}",
                    destination, key, delaySeconds, i, retryTimes, e);
            }
        }

        saveFailedMessage(destination, key, payload);
        return false;
    }

    /**
     * 拼接 RocketMQ Spring 使用的 destination。
     */
    private String destination(String topic, String tag) {
        return topic + ":" + tag;
    }

    /**
     * 构建统一消息，设置 key 便于控制台排查。
     */
    private Message<String> buildMessage(String key, String payload) {
        return MessageBuilder.withPayload(payload)
            .setHeader(RocketMQHeaders.KEYS, key)
            .build();
    }

    /**
     * 多次发送仍失败时写入 Redis，方便后续人工补偿。
     */
    private void saveFailedMessage(String destination, String key, String payload) {
        try {
            redisUtil.setRequired("mq:failed:send:" + destination + ":" + key, payload, FAILED_MESSAGE_TTL);
        } catch (Exception e) {
            log.error("RocketMQ 失败消息暂存 Redis 也失败: destination={}, key={}", destination, key, e);
        }
    }
}
