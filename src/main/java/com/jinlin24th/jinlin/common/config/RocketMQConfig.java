package com.jinlin24th.jinlin.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 业务配置类。
 * <p>
 * 这里只承载电商业务层需要感知的 topic、tag、消费组和延迟时间配置；
 * NameServer、生产者组等底层连接配置仍交给 rocketmq-spring-boot-starter 读取。
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.mq")
public class RocketMQConfig {

    /**
     * 是否启用 RocketMQ，开发环境默认关闭，避免没有 MQ 服务时影响启动。
     */
    private Boolean enabled = false;

    /**
     * 短信消息 Topic。
     */
    private String topicSms = "mall_sms_topic";

    /**
     * 短信验证码 Tag。
     */
    private String tagSmsVerifyCode = "VERIFY_CODE";

    /**
     * 下单成功通知短信 Tag。
     */
    private String tagSmsOrderSuccess = "ORDER_SUCCESS";

    /**
     * 订单延迟消息 Topic。
     */
    private String topicOrderDelay = "mall_order_delay_topic";

    /**
     * 订单超时取消 Tag。
     */
    private String tagOrderTimeoutCancel = "TIMEOUT_CANCEL";

    /**
     * 操作日志 Topic。
     */
    private String topicOperationLog = "mall_operation_log_topic";

    /**
     * 后台增删改操作日志 Tag。
     */
    private String tagOperationLogCrud = "ADMIN_CRUD";

    /**
     * 短信消费组。
     */
    private String consumerGroupSms = "mall_sms_consumer_group";

    /**
     * 订单超时取消消费组。
     */
    private String consumerGroupOrderTimeout = "mall_order_timeout_consumer_group";

    /**
     * 操作日志消费组。
     */
    private String consumerGroupOperationLog = "mall_operation_log_consumer_group";

    /**
     * 生产者主动发送重试次数。
     */
    private Integer producerRetryTimes = 3;

    /**
     * 发送超时时间，单位毫秒。
     */
    private Long sendTimeoutMs = 3000L;

    /**
     * 订单超时自动取消延迟秒数，默认 30 分钟。
     */
    private Long orderTimeoutCancelDelaySeconds = 30 * 60L;
}
