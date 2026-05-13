package com.jinlin24th.jinlin.common.mq.producer;

import com.jinlin24th.jinlin.common.config.RocketMQConfig;
import com.jinlin24th.jinlin.common.mq.dto.SmsMessageDTO;
import com.jinlin24th.jinlin.common.mq.support.RocketMQSendHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 短信消息生产者。
 * <p>
 * 负责将验证码、下单成功通知等短信任务投递到 RocketMQ，接口返回不等待真实短信平台发送完成。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class SmsMessageProducer {

    private final RocketMQConfig rocketMQConfig;
    private final RocketMQSendHelper rocketMQSendHelper;

    public SmsMessageProducer(RocketMQConfig rocketMQConfig, RocketMQSendHelper rocketMQSendHelper) {
        this.rocketMQConfig = rocketMQConfig;
        this.rocketMQSendHelper = rocketMQSendHelper;
    }

    /**
     * 发送注册验证码短信消息。
     */
    public boolean sendVerifyCode(String phone, String code) {
        SmsMessageDTO message = buildMessage(phone, "VERIFY_CODE", "SMS_VERIFY_CODE",
            Map.of("code", code), phone);
        return rocketMQSendHelper.syncSendWithRetry(
            rocketMQConfig.getTopicSms(),
            rocketMQConfig.getTagSmsVerifyCode(),
            message.getMessageKey(),
            message
        );
    }

    /**
     * 发送下单成功通知短信消息。
     */
    public boolean sendOrderSuccess(String phone, String orderNo) {
        SmsMessageDTO message = buildMessage(phone, "ORDER_SUCCESS", "SMS_ORDER_SUCCESS",
            Map.of("orderNo", orderNo), orderNo);
        return rocketMQSendHelper.syncSendWithRetry(
            rocketMQConfig.getTopicSms(),
            rocketMQConfig.getTagSmsOrderSuccess(),
            message.getMessageKey(),
            message
        );
    }

    /**
     * 组装短信消息体，保证所有短信消息结构一致。
     */
    private SmsMessageDTO buildMessage(String phone, String smsType, String templateCode, Map<String, String> params, String bizNo) {
        SmsMessageDTO message = new SmsMessageDTO();
        message.setMessageKey("sms:" + smsType + ":" + bizNo + ":" + UUID.randomUUID());
        message.setPhone(phone);
        message.setSmsType(smsType);
        message.setTemplateCode(templateCode);
        message.setParams(params);
        message.setBizNo(bizNo);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }
}
