package com.jinlin24th.jinlin.common.mq.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 短信消息 DTO。
 * <p>
 * 所有短信类消息统一走该结构，消费者根据 smsType 区分验证码、下单成功通知等场景。
 */
@Data
public class SmsMessageDTO implements Serializable {

    /**
     * 消息唯一键，用于生产排查和消费幂等。
     */
    private String messageKey;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 短信类型：VERIFY_CODE-验证码，ORDER_SUCCESS-下单成功通知。
     */
    private String smsType;

    /**
     * 短信模板编码，方便后续接入真实短信平台。
     */
    private String templateCode;

    /**
     * 模板参数，必须能被 JSON 序列化。
     */
    private Map<String, String> params;

    /**
     * 业务编号，例如验证码场景可放手机号，订单通知场景可放订单号。
     */
    private String bizNo;

    /**
     * 消息创建时间。
     */
    private LocalDateTime createTime;
}
