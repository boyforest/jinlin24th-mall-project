package com.jinlin24th.jinlin.common.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.mq.dto.OperationLogMessageDTO;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import com.jinlin24th.jinlin.service.SysOperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 操作日志消费者。
 * <p>
 * 将后台增删改日志异步写入 sys_operation_log 表，并通过 Redis 防止重复落库。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${app.mq.topic-operation-log}",
    selectorExpression = "${app.mq.tag-operation-log-crud}",
    consumerGroup = "${app.mq.consumer-group-operation-log}",
    maxReconsumeTimes = 3
)
public class OperationLogConsumer implements RocketMQListener<String> {

    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(30);
    private static final Duration BAD_MESSAGE_TTL = Duration.ofDays(7);

    private final RedisUtil redisUtil;
    private final SysOperationLogService sysOperationLogService;

    public OperationLogConsumer(RedisUtil redisUtil, SysOperationLogService sysOperationLogService) {
        this.redisUtil = redisUtil;
        this.sysOperationLogService = sysOperationLogService;
    }

    /**
     * 消费操作日志消息，失败时抛异常触发 MQ 重试。
     */
    @Override
    public void onMessage(String message) {
        OperationLogMessageDTO dto = parseMessage(message);
        if (dto == null) {
            return;
        }

        String consumedKey = "mq:consumed:operation-log:" + dto.getMessageKey();
        if (!redisUtil.setIfAbsentRequired(consumedKey, "1", IDEMPOTENT_TTL)) {
            log.info("操作日志消息重复消费，已忽略: messageKey={}", dto.getMessageKey());
            return;
        }

        try {
            sysOperationLogService.saveFromMessage(dto);
            log.info("操作日志已异步写入: module={}, operation={}, uri={}",
                dto.getModule(), dto.getOperation(), dto.getRequestUri());
        } catch (Exception e) {
            redisUtil.delete(consumedKey);
            log.error("操作日志消费失败，等待 RocketMQ 重试: messageKey={}", dto.getMessageKey(), e);
            throw e;
        }
    }

    /**
     * 解析操作日志消息，坏消息暂存后不再重试。
     */
    private OperationLogMessageDTO parseMessage(String message) {
        try {
            OperationLogMessageDTO dto = JSON.parseObject(message, OperationLogMessageDTO.class);
            if (dto == null || dto.getMessageKey() == null || dto.getMessageKey().isBlank()) {
                saveBadMessage(message, "操作日志消息缺少 messageKey");
                return null;
            }
            return dto;
        } catch (Exception e) {
            saveBadMessage(message, "操作日志消息 JSON 解析失败");
            log.error("操作日志消息 JSON 解析失败", e);
            return null;
        }
    }

    /**
     * 保存无法解析或字段不完整的坏消息。
     */
    private void saveBadMessage(String message, String reason) {
        try {
            String key = "mq:bad:operation-log:" + UUID.randomUUID();
            redisUtil.setRequired(key, reason + " | " + message, BAD_MESSAGE_TTL);
            log.warn("操作日志坏消息已暂存: key={}, reason={}", key, reason);
        } catch (Exception e) {
            log.error("操作日志坏消息暂存失败", e);
        }
    }
}
