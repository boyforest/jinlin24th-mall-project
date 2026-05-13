package com.jinlin24th.jinlin.common.mq;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.mq.consumer.OperationLogConsumer;
import com.jinlin24th.jinlin.common.mq.dto.OperationLogMessageDTO;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import com.jinlin24th.jinlin.service.SysOperationLogService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作日志消费者测试。
 */
class OperationLogConsumerTest {

    /**
     * 验证操作日志消息会落到日志服务。
     */
    @Test
    void onMessageShouldSaveOperationLog() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        SysOperationLogService logService = mock(SysOperationLogService.class);
        when(redisUtil.setIfAbsentRequired(any(), eq("1"), any(Duration.class))).thenReturn(true);

        OperationLogConsumer consumer = new OperationLogConsumer(redisUtil, logService);
        OperationLogMessageDTO message = new OperationLogMessageDTO();
        message.setMessageKey("oplog:test");
        message.setModule("product");
        message.setOperation("CREATE");
        message.setRequestMethod("POST");
        message.setRequestUri("/admin/product");
        message.setStatus(1);
        message.setCreateTime(LocalDateTime.now());

        consumer.onMessage(JSON.toJSONString(message));

        verify(logService).saveFromMessage(any(OperationLogMessageDTO.class));
    }
}
