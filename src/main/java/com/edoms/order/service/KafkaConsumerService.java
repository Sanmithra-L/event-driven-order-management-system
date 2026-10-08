package com.edoms.order.service;

import com.edoms.order.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events")
    public void consumePaymentEvent(String message) throws JsonProcessingException {
        orderService.handlePaymentEvent(objectMapper.readValue(message, PaymentProcessedEvent.class));
    }

    @KafkaListener(topics = "inventory-events")
    public void consumeInventoryEvent(String message) throws JsonProcessingException {
        orderService.handleInventoryEvent(objectMapper.readValue(message, InventoryUpdatedEvent.class));
    }
}
