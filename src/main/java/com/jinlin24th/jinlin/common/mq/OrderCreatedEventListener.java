package com.jinlin24th.jinlin.common.mq;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class OrderCreatedEventListener {

    private final OrderCreatedDispatcher dispatcher;

    public OrderCreatedEventListener(OrderCreatedDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCreatedAfterCommit(OrderCreatedSpringEvent event) {
        dispatcher.dispatch(event.getPayload());
    }
}
