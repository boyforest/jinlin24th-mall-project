package com.jinlin24th.jinlin.common.mq;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderCreatedSpringEvent extends ApplicationEvent {
    private final OrderCreatedEvent payload;

    public OrderCreatedSpringEvent(Object source, OrderCreatedEvent payload) {
        super(source);
        this.payload = payload;
    }
}
