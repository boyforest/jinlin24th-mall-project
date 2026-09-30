package com.jinlin24th.jinlin.common.mq;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.mq.consumer.OrderTimeoutCancelConsumer;
import com.jinlin24th.jinlin.common.mq.dto.OrderTimeoutCancelMessageDTO;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import com.jinlin24th.jinlin.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单超时取消消费者的幂等标记语义测试。
 * <p>
 * 修复的缺陷：原实现先 SETNX 写"已消费"标记、再执行业务，key 的 TTL 是 7 天。
 * 如果 JVM 在写入标记之后、业务完成之前崩溃（或数据库卡死导致 Pod 被杀），
 * catch 分支的 delete 不会执行；RocketMQ 重投时看到标记存在就当作"重复消息"直接 ack，
 * 结果是**订单永远不被取消、库存永远不释放**，且没有任何地方会发现问题。
 * <p>
 * 正确语义：只有业务**成功**后才写已消费标记。
 * 业务本身已由状态 CAS 保证幂等，因此即使重复执行也安全。
 */
class OrderTimeoutCancelConsumerIdempotencyTest {

    private RedisUtil redisUtil;
    private OrderService orderService;
    private OrderTimeoutCancelConsumer consumer;

    @BeforeEach
    void setUp() {
        redisUtil = mock(RedisUtil.class);
        orderService = mock(OrderService.class);
        consumer = new OrderTimeoutCancelConsumer(redisUtil, orderService);
    }

    private String message(String orderNo) {
        OrderTimeoutCancelMessageDTO dto = new OrderTimeoutCancelMessageDTO();
        dto.setMessageKey("order:timeout:" + orderNo);
        dto.setOrderNo(orderNo);
        dto.setOrderId(1L);
        dto.setUserId(1L);
        dto.setCreateTime(LocalDateTime.now());
        dto.setExpireTime(LocalDateTime.now().plusMinutes(30));
        return JSON.toJSONString(dto);
    }

    @Test
    @DisplayName("业务成功后才写已消费标记")
    void shouldMarkConsumedOnlyAfterSuccess() {
        when(redisUtil.exists(anyString())).thenReturn(false);
        when(orderService.cancelUnpaidOrder(eq("JL_OK"), any())).thenReturn(true);

        consumer.onMessage(message("JL_OK"));

        verify(orderService).cancelUnpaidOrder(eq("JL_OK"), any());
        verify(redisUtil).setRequired(contains("JL_OK"), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("业务失败时绝不能留下已消费标记（否则重投被丢弃、订单永不取消）")
    void failureMustNotLeaveConsumedMarker() {
        when(redisUtil.exists(anyString())).thenReturn(false);
        when(orderService.cancelUnpaidOrder(eq("JL_FAIL"), any()))
            .thenThrow(new IllegalStateException("数据库不可用"));

        assertThrows(RuntimeException.class, () -> consumer.onMessage(message("JL_FAIL")),
            "业务失败必须抛出以触发 MQ 重试");

        // 关键断言：任何"已消费"标记都不许写入
        verify(redisUtil, never()).setRequired(anyString(), anyString(), any(Duration.class));
        verify(redisUtil, never()).setIfAbsentRequired(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("已消费过的消息直接跳过，不重复执行业务")
    void alreadyConsumedShouldBeSkipped() {
        when(redisUtil.exists(anyString())).thenReturn(true);

        consumer.onMessage(message("JL_DUP"));

        verify(redisUtil).exists(contains("JL_DUP"));
        verify(orderService, never()).cancelUnpaidOrder(anyString(), anyString());
    }

    @Test
    @DisplayName("业务返回 false（CAS 未命中）也应视为处理成功并标记，避免无意义重试")
    void notCancelledButHandledShouldStillBeMarked() {
        when(redisUtil.exists(anyString())).thenReturn(false);
        when(orderService.cancelUnpaidOrder(eq("JL_SKIP"), any())).thenReturn(false);

        consumer.onMessage(message("JL_SKIP"));

        verify(redisUtil).setRequired(contains("JL_SKIP"), eq("1"), any(Duration.class));
    }
}
