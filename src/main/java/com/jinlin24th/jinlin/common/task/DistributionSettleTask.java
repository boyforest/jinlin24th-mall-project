package com.jinlin24th.jinlin.common.task;

import com.jinlin24th.jinlin.service.DistributionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分销佣金“到期可结算”定时任务。
 * <p>
 * 每天凌晨 2:30 将已到可结算时间（订单已完成且超过 settle_days 天）的佣金记录
 * 从 0-待结算 变为 1-可结算，供财务按名单线下打款。
 */
@Slf4j
@Component
public class DistributionSettleTask {

    private final DistributionService distributionService;

    public DistributionSettleTask(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @Scheduled(cron = "0 30 2 * * ?")
    public void markEligibleSettleable() {
        try {
            int count = distributionService.markEligibleDistributionsSettleable();
            log.info("分销佣金到期可结算任务执行完成，本次变为可结算: count={}", count);
        } catch (Exception e) {
            log.error("分销佣金到期可结算任务执行失败", e);
        }
    }
}
