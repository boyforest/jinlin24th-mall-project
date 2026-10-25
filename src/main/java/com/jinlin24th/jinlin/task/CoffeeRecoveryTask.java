package com.jinlin24th.jinlin.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jinlin24th.jinlin.common.coffee.CoffeeTime;
import com.jinlin24th.jinlin.common.config.CoffeePayConfig;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.service.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 复用应用定时调度。所有候选均来自持久化事实，重启不丢任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoffeeRecoveryTask {
    private final CoffeePayConfig config;
    private final CoffeeOrderMapper orders;
    private final CoffeePaymentMapper payments;
    private final CoffeeRefundMapper refunds;
    private final CoffeeNotificationTaskMapper notifications;
    private final CoffeeOrderService service;
    private final CoffeePaymentService payment;
    private final CoffeeNotificationService notification;

    @Scheduled(
            fixedDelayString = "${app.coffee.recovery-delay-ms:15000}",
            initialDelayString = "${app.coffee.recovery-delay-ms:15000}")
    public void recover() {
        for (var o :
                orders.selectList(
                        Wrappers.<CoffeeOrder>lambdaQuery()
                                .eq(CoffeeOrder::getFulfillmentStatus, "WAIT_PAYMENT")
                                .eq(CoffeeOrder::getPaymentStatus, "UNPAID")
                                .le(CoffeeOrder::getExpiresAt, CoffeeTime.now())
                                .orderByAsc(CoffeeOrder::getId)
                                .last("LIMIT 50")))
            run(
                    o.getId(),
                    () -> {
                        service.cancel(null, o.getId(), true);
                    });
        if (config.isPaymentEnabled()) {
            for (var p :
                    payments.selectList(
                            Wrappers.<CoffeePayment>lambdaQuery()
                                    .eq(CoffeePayment::getStatus, "UNPAID")
                                    .eq(CoffeePayment::getInitiated, true)
                                    .le(CoffeePayment::getNextQueryAt, CoffeeTime.now())
                                    .orderByAsc(CoffeePayment::getNextQueryAt)
                                    .last("LIMIT 50")))
                run(
                        p.getOrderId(),
                        () -> {
                            if (service.claimPayment(p.getOrderId()))
                                payment.reconcile(p.getOrderId());
                        });
            for (var r :
                    refunds.selectList(
                            Wrappers.<CoffeeRefund>lambdaQuery()
                                    .eq(CoffeeRefund::getStatus, "PENDING")
                                    .le(CoffeeRefund::getNextQueryAt, CoffeeTime.now())
                                    .orderByAsc(CoffeeRefund::getNextQueryAt)
                                    .last("LIMIT 50")))
                run(r.getOrderId(), () -> payment.recoverRefund(r.getOrderId()));
        }
        for (var n :
                notifications.selectList(
                        Wrappers.<CoffeeNotificationTask>lambdaQuery()
                                .eq(CoffeeNotificationTask::getStatus, "PENDING")
                                .orderByAsc(CoffeeNotificationTask::getId)
                                .last("LIMIT 50")))
            run(n.getOrderId(), () -> notification.send(n.getId()));
    }

    private void run(Long id, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("咖啡恢复待下轮处理 orderId={}, errorType={}", id, e.getClass().getSimpleName());
        }
    }
}
