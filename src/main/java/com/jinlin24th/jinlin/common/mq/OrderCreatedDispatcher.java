package com.jinlin24th.jinlin.common.mq;

public interface OrderCreatedDispatcher {
    void dispatch(OrderCreatedEvent event);
}
