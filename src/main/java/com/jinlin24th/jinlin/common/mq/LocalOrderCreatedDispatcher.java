package com.jinlin24th.jinlin.common.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalOrderCreatedDispatcher implements OrderCreatedDispatcher {
    @Override
    public void dispatch(OrderCreatedEvent event) {
        log.info("MQ 未开启，订单创建事件仅本地记录: orderNo={}, userId={}, amount={}",
            event.getOrderNo(), event.getUserId(), event.getTotalAmount());
    }
}
