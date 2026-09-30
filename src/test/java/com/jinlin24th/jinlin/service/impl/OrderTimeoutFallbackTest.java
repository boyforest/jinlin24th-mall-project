package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.InventoryLogService;
import com.jinlin24th.jinlin.service.InventoryService;
import com.jinlin24th.jinlin.service.OrderItemService;
import com.jinlin24th.jinlin.service.OrderMasterService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单超时兜底取消的测试。
 * <p>
 * 修复的问题：`app.mq.enabled` 默认为 false，订单超时取消依赖 RocketMQ 延迟消息；
 * MQ 关闭时没有任何兜底，下单即占用库存（OrderServiceImpl.occupyInventory），
 * 未付款订单会把库存永久锁死，只能靠管理端逐单手工取消。
 * <p>
 * 兜底实现复用已有的 `cancelUnpaidOrder`（内含状态 CAS，天然幂等），
 * 因此即使 MQ 也同时触达，也不会重复取消或重复归还库存。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderTimeoutFallbackTest {

    private static final int LIMIT = 200;

    @Mock
    private OrderMasterService orderMasterService;
    @Mock
    private OrderItemService orderItemService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private InventoryLogService inventoryLogService;
    @Mock
    private AppUserService appUserService;
    @Mock
    private LambdaQueryChainWrapper<OrderMaster> orderMasterQuery;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(OrderMaster.class);

        OrderServiceImpl real = new OrderServiceImpl();
        ReflectionTestUtils.setField(real, "orderMasterService", orderMasterService);
        ReflectionTestUtils.setField(real, "orderItemService", orderItemService);
        ReflectionTestUtils.setField(real, "inventoryService", inventoryService);
        ReflectionTestUtils.setField(real, "inventoryLogService", inventoryLogService);
        ReflectionTestUtils.setField(real, "appUserService", appUserService);
        ReflectionTestUtils.setField(real, "effectivePriceService", new com.jinlin24th.jinlin.service.EffectivePriceService());
        ReflectionTestUtils.setField(real, "timeoutCancelSeconds", 1800L);
        service = spy(real);

        when(orderMasterService.lambdaQuery()).thenReturn(orderMasterQuery);
        when(orderMasterQuery.eq(any(), any())).thenReturn(orderMasterQuery);
        when(orderMasterQuery.lt(any(), any())).thenReturn(orderMasterQuery);
        when(orderMasterQuery.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class)))
            .thenReturn(orderMasterQuery);
        when(orderMasterQuery.last(anyString())).thenReturn(orderMasterQuery);
    }

    private static void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }

    private OrderMaster pendingOrder(String orderNo) {
        OrderMaster order = new OrderMaster();
        order.setOrderNo(orderNo);
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now().minusHours(1));
        return order;
    }

    @Test
    @DisplayName("MQ 关闭时兜底必须能取消超时未付款订单（库存锁死的回归）")
    void shouldCancelTimeoutPendingOrders() {
        when(orderMasterQuery.list()).thenReturn(List.of(
            pendingOrder("JL-A"), pendingOrder("JL-B"), pendingOrder("JL-C")));
        doReturn(true).when(service).cancelUnpaidOrder(anyString(), anyString());

        int cancelled = service.cancelTimeoutPendingOrders(LIMIT);

        assertEquals(3, cancelled);
        verify(service).cancelUnpaidOrder(org.mockito.ArgumentMatchers.eq("JL-A"), anyString());
        verify(service).cancelUnpaidOrder(org.mockito.ArgumentMatchers.eq("JL-B"), anyString());
        verify(service).cancelUnpaidOrder(org.mockito.ArgumentMatchers.eq("JL-C"), anyString());
    }

    @Test
    @DisplayName("没有超时订单时不做任何取消，返回 0")
    void shouldDoNothingWhenNoTimeoutOrders() {
        when(orderMasterQuery.list()).thenReturn(List.of());

        int cancelled = service.cancelTimeoutPendingOrders(LIMIT);

        assertEquals(0, cancelled);
        verify(service, never()).cancelUnpaidOrder(anyString(), anyString());
    }

    @Test
    @DisplayName("单个订单取消失败不能中断整批（其余订单仍要处理）")
    void oneFailureMustNotAbortTheBatch() {
        when(orderMasterQuery.list()).thenReturn(List.of(
            pendingOrder("JL-OK-1"), pendingOrder("JL-BAD"), pendingOrder("JL-OK-2")));
        doReturn(true).when(service).cancelUnpaidOrder(anyString(), anyString());
        doThrow(new IllegalStateException("库存记录缺失")).when(service)
            .cancelUnpaidOrder(org.mockito.ArgumentMatchers.eq("JL-BAD"), anyString());

        int cancelled = service.cancelTimeoutPendingOrders(LIMIT);

        assertEquals(2, cancelled, "只有失败那一单不计入，其余必须照常取消");
        verify(service).cancelUnpaidOrder(org.mockito.ArgumentMatchers.eq("JL-OK-1"), anyString());
        verify(service).cancelUnpaidOrder(org.mockito.ArgumentMatchers.eq("JL-OK-2"), anyString());
    }

    @Test
    @DisplayName("CAS 未命中（已被其他路径取消）不计入成功数，且不视为错误")
    void notCancelledBecauseAlreadyHandledIsNotCounted() {
        when(orderMasterQuery.list()).thenReturn(List.of(pendingOrder("JL-DONE"), pendingOrder("JL-NEW")));
        doReturn(false).when(service).cancelUnpaidOrder(org.mockito.ArgumentMatchers.eq("JL-DONE"), anyString());
        doReturn(true).when(service).cancelUnpaidOrder(org.mockito.ArgumentMatchers.eq("JL-NEW"), anyString());

        int cancelled = service.cancelTimeoutPendingOrders(LIMIT);

        assertEquals(1, cancelled);
    }

    @Test
    @DisplayName("必须按「超时时间」过滤，且带批量上限，避免一次拉起全表")
    void shouldFilterByTimeoutAndApplyLimit() {
        when(orderMasterQuery.list()).thenReturn(List.of(pendingOrder("JL-A")));

        service.cancelTimeoutPendingOrders(50);

        // 批量上限必须真正传到底层 SQL（last("LIMIT n")）
        org.mockito.ArgumentCaptor<String> lastCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(orderMasterQuery).last(lastCaptor.capture());
        assertTrue(lastCaptor.getValue().contains("50"),
            "必须把批量上限传给 SQL，否则一次可能加载全部待付款订单：" + lastCaptor.getValue());

        // 超时过滤必须基于创建时间下界
        org.mockito.ArgumentCaptor<Object> timeCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(orderMasterQuery).lt(any(), timeCaptor.capture());
        assertTrue(timeCaptor.getValue() instanceof LocalDateTime,
            "应按 create_time 下界过滤超时订单，实际：" + timeCaptor.getValue());

        LocalDateTime cutoff = (LocalDateTime) timeCaptor.getValue();
        LocalDateTime expected = LocalDateTime.now().minusSeconds(1800L);
        long diffSeconds = Math.abs(java.time.Duration.between(expected, cutoff).getSeconds());
        assertTrue(diffSeconds <= 60,
            "下界应约为 现在-1800秒，实际偏差 " + diffSeconds + " 秒");
    }

    @Test
    @DisplayName("必须只查待付款订单，绝不动已支付/已完成订单")
    void shouldOnlyScanPendingPaymentOrders() {
        when(orderMasterQuery.list()).thenReturn(List.of());

        service.cancelTimeoutPendingOrders(LIMIT);

        // 第一个 eq 是状态条件，值必须是 0（待付款）
        org.mockito.ArgumentCaptor<Object> valueCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(orderMasterQuery).eq(any(), valueCaptor.capture());
        assertEquals(0, ((Number) valueCaptor.getValue()).intValue(),
            "只允许扫描 status=0（待付款）的订单");
    }

    @Test
    @DisplayName("兜底任务与 MQ 并发触达时不应重复取消（CAS 幂等）")
    void shouldBeIdempotentWithConcurrentMqPath() {
        when(orderMasterQuery.list()).thenReturn(List.of(pendingOrder("JL-RACE")));
        // 第一次真取消，第二次（MQ 已抢先）CAS 未命中
        doReturn(true, false).when(service).cancelUnpaidOrder(anyString(), anyString());

        int first = service.cancelTimeoutPendingOrders(LIMIT);
        int second = service.cancelTimeoutPendingOrders(LIMIT);

        assertEquals(1, first);
        assertEquals(0, second, "已取消的订单不应被重复统计/重复归还库存");
        verify(service, times(2)).cancelUnpaidOrder(anyString(), anyString());
    }
}
