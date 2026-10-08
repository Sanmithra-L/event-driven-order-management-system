package com.edoms.order.service;

import com.edoms.order.dto.OrderCreatedEvent;
import com.edoms.order.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final OutboxService outbox;

    public void sendOrderCreatedEvent(OrderCreatedEvent event) {
        outbox.enqueue("order-events", event.getOrderId(), event);
    }
}
