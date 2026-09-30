package com.jinlin24th.jinlin.common.task;

import com.jinlin24th.jinlin.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单超时兜底定时任务的调度外壳测试。
 * <p>
 * 关注两件事：
 * <ol>
 *   <li>必须把批量上限传给 service（否则可能一次拉起全部待付款订单）；</li>
 *   <li>任何异常都不能抛出调度线程 —— 单轮失败不应影响后续轮次继续兜底。</li>
 * </ol>
 * 业务逻辑本身（选哪些单、如何计数、失败如何处理）由 OrderTimeoutFallbackTest 覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderTimeoutFallbackTaskTest {

    @Mock
    private OrderService orderService;

    private OrderTimeoutFallbackTask task;

    @BeforeEach
    void setUp() {
        task = new OrderTimeoutFallbackTask(orderService);
        ReflectionTestUtils.setField(task, "batchSize", 200);
    }

    @Test
    @DisplayName("调度时必须把批量上限传给 service")
    void shouldPassBatchSizeToService() {
        when(orderService.cancelTimeoutPendingOrders(200)).thenReturn(0);

        task.cancelTimeoutOrders();

        verify(orderService).cancelTimeoutPendingOrders(200);
    }

    @Test
    @DisplayName("单轮执行失败不得抛出，否则会影响后续调度")
    void shouldSwallowExceptionsSoSchedulingContinues() {
        when(orderService.cancelTimeoutPendingOrders(200))
            .thenThrow(new RuntimeException("数据库暂时不可用"));

        assertDoesNotThrow(task::cancelTimeoutOrders,
            "定时任务必须自己吞掉异常并告警，不能让异常冒泡到调度线程");
    }
}
