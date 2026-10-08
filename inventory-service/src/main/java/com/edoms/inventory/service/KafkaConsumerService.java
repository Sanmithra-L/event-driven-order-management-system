package com.edoms.inventory.service;

import com.edoms.inventory.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    private final InventoryService service;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events")
    public void consumeOrderEvent(String message) throws JsonProcessingException {
        OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
        OrderEventValidation.validate(event);
        if ("ORDER_CANCELLED".equals(event.getEventType())) {
            service.releaseInventory(event);
        } else {
            service.reserveInventory(event);
        }
    }
}
