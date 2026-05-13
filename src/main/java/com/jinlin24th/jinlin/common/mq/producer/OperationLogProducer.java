package com.jinlin24th.jinlin.common.mq.producer;

import com.jinlin24th.jinlin.common.config.RocketMQConfig;
import com.jinlin24th.jinlin.common.mq.dto.OperationLogMessageDTO;
import com.jinlin24th.jinlin.common.mq.support.RocketMQSendHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 操作日志消息生产者。
 * <p>
 * 将后台增删改操作异步投递到 RocketMQ，避免日志落库拖慢业务接口。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class OperationLogProducer {

    private final RocketMQConfig rocketMQConfig;
    private final RocketMQSendHelper rocketMQSendHelper;

    public OperationLogProducer(RocketMQConfig rocketMQConfig, RocketMQSendHelper rocketMQSendHelper) {
        this.rocketMQConfig = rocketMQConfig;
        this.rocketMQSendHelper = rocketMQSendHelper;
    }

    /**
     * 发送操作日志消息。
     */
    public boolean send(OperationLogMessageDTO message) {
        return rocketMQSendHelper.syncSendWithRetry(
            rocketMQConfig.getTopicOperationLog(),
            rocketMQConfig.getTagOperationLogCrud(),
            message.getMessageKey(),
            message
        );
    }
}
