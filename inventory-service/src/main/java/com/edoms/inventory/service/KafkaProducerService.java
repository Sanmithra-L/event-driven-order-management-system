package com.edoms.inventory.service;

import com.edoms.inventory.dto.InventoryUpdatedEvent;
import com.edoms.inventory.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final OutboxService outbox;

    public void sendInventoryUpdatedEvent(InventoryUpdatedEvent event) {
        outbox.enqueue("inventory-events", event.getOrderId(), event);
    }
}
