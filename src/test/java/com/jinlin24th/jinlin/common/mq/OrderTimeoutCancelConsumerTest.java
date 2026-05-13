package com.jinlin24th.jinlin.common.mq;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.mq.consumer.OrderTimeoutCancelConsumer;
import com.jinlin24th.jinlin.common.mq.dto.OrderTimeoutCancelMessageDTO;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import com.jinlin24th.jinlin.service.OrderService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单超时取消消费者测试。
 */
class OrderTimeoutCancelConsumerTest {

    /**
     * 验证未重复消费时，会调用订单取消服务。
     */
    @Test
    void onMessageShouldCancelUnpaidOrder() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        OrderService orderService = mock(OrderService.class);
        when(redisUtil.setIfAbsentRequired(any(), eq("1"), any(Duration.class))).thenReturn(true);
        when(orderService.cancelUnpaidOrder(eq("JL_TEST"), any())).thenReturn(true);

        OrderTimeoutCancelConsumer consumer = new OrderTimeoutCancelConsumer(redisUtil, orderService);
        OrderTimeoutCancelMessageDTO message = new OrderTimeoutCancelMessageDTO();
        message.setMessageKey("order:timeout:JL_TEST");
        message.setOrderNo("JL_TEST");
        message.setOrderId(1L);
        message.setUserId(1L);
        message.setCreateTime(LocalDateTime.now());
        message.setExpireTime(LocalDateTime.now().plusMinutes(30));

        consumer.onMessage(JSON.toJSONString(message));

        verify(orderService).cancelUnpaidOrder(eq("JL_TEST"), any());
    }
}
