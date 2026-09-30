package com.jinlin24th.jinlin.common.task;

import com.jinlin24th.jinlin.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时兜底取消任务。
 * <p>
 * 存在意义：订单在**下单时**就占用库存（{@code OrderServiceImpl.occupyInventory}），
 * 而超时取消原本只依赖 RocketMQ 延迟消息，且 {@code app.mq.enabled} 默认为 false。
 * 这意味着默认部署形态下未付款订单会**永久锁死库存**，只能靠管理端逐单手工取消。
 * <p>
 * 本任务与 MQ 延迟消息互补而非替代：
 * <ul>
 *   <li>MQ 开启：延迟消息到点即时取消，本任务几乎无事可做（CAS 返回 false，不计入成功数）；</li>
 *   <li>MQ 关闭或 Broker 不可用：本任务按固定间隔兜底，保证库存最终能被释放。</li>
 * </ul>
 * 两条路径都走 {@code cancelUnpaidOrder} 的状态 CAS，因此并发触达不会重复取消或重复归还库存。
 * <p>
 * 配置：
 * <pre>
 * app.order.timeout-cancel-seconds      超时判定时长，默认 1800（30 分钟）
 * app.order.timeout-scan-interval-ms    扫描间隔，默认 300000（5 分钟）
 * app.order.timeout-scan-batch-size     单次处理上限，默认 200
 * </pre>
 */
@Slf4j
@Component
public class OrderTimeoutFallbackTask {

    private final OrderService orderService;

    @Value("${app.order.timeout-scan-batch-size:200}")
    private int batchSize;

    public OrderTimeoutFallbackTask(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 按固定间隔扫描并取消超时未付款订单。
     * <p>
     * 用 {@code fixedDelay} 而非 {@code fixedRate}：上一轮没跑完时不应叠加下一轮，
     * 避免在数据库慢查询时把线程池打满。
     */
    @Scheduled(
        fixedDelayString = "${app.order.timeout-scan-interval-ms:300000}",
        initialDelayString = "${app.order.timeout-scan-initial-delay-ms:60000}"
    )
    public void cancelTimeoutOrders() {
        try {
            int cancelled = orderService.cancelTimeoutPendingOrders(batchSize);
            if (cancelled > 0) {
                log.info("订单超时兜底任务执行完成，本次取消 {} 笔未付款订单", cancelled);
            }
        } catch (Exception e) {
            // 定时任务绝不能抛出异常，否则会影响后续调度
            log.error("[ORDER-ALERT] 订单超时兜底任务执行失败", e);
        }
    }
}
