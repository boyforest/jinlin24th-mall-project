package com.jinlin24th.jinlin.common.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.mq.dto.SmsMessageDTO;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 短信消息消费者。
 * <p>
 * 当前项目不额外引入短信 SDK，因此这里先做“模拟发送 + 详细日志”；
 * 后续接入阿里云/腾讯云短信时，只替换 sendSms 方法内部实现即可。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${app.mq.topic-sms}",
    selectorExpression = "${app.mq.tag-sms-verify-code} || ${app.mq.tag-sms-order-success}",
    consumerGroup = "${app.mq.consumer-group-sms}",
    maxReconsumeTimes = 3
)
public class SmsMessageConsumer implements RocketMQListener<String> {

    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);
    private static final Duration BAD_MESSAGE_TTL = Duration.ofDays(7);

    private final RedisUtil redisUtil;

    public SmsMessageConsumer(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    /**
     * 消费短信消息，失败时抛出异常让 RocketMQ 重试，超过重试次数后进入死信队列。
     */
    @Override
    public void onMessage(String message) {
        SmsMessageDTO dto = parseMessage(message);
        if (dto == null) {
            return;
        }

        String consumedKey = "mq:consumed:sms:" + dto.getMessageKey();
        if (!redisUtil.setIfAbsentRequired(consumedKey, "1", IDEMPOTENT_TTL)) {
            log.info("短信消息重复消费，已忽略: messageKey={}", dto.getMessageKey());
            return;
        }

        try {
            sendSms(dto);
        } catch (Exception e) {
            redisUtil.delete(consumedKey);
            log.error("短信消息消费失败，等待 RocketMQ 重试: messageKey={}, phone={}",
                dto.getMessageKey(), dto.getPhone(), e);
            throw e;
        }
    }

    /**
     * 解析短信消息，坏消息落 Redis 方便排查，不再重试。
     */
    private SmsMessageDTO parseMessage(String message) {
        try {
            SmsMessageDTO dto = JSON.parseObject(message, SmsMessageDTO.class);
            if (dto == null || dto.getMessageKey() == null || dto.getPhone() == null) {
                saveBadMessage(message, "短信消息缺少必要字段");
                return null;
            }
            return dto;
        } catch (Exception e) {
            saveBadMessage(message, "短信消息 JSON 解析失败");
            log.error("短信消息 JSON 解析失败", e);
            return null;
        }
    }

    /**
     * 模拟短信发送，真实项目在这里调用短信平台 SDK。
     */
    private void sendSms(SmsMessageDTO dto) {
        log.info("模拟发送短信成功: type={}, phone={}, templateCode={}, params={}, bizNo={}",
            dto.getSmsType(), dto.getPhone(), dto.getTemplateCode(), dto.getParams(), dto.getBizNo());
    }

    /**
     * 保存无法解析或字段不完整的坏消息。
     */
    private void saveBadMessage(String message, String reason) {
        try {
            String key = "mq:bad:sms:" + UUID.randomUUID();
            redisUtil.setRequired(key, reason + " | " + message, BAD_MESSAGE_TTL);
            log.warn("短信坏消息已暂存: key={}, reason={}", key, reason);
        } catch (Exception e) {
            log.error("短信坏消息暂存失败", e);
        }
    }
}
