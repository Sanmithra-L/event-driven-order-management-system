package com.edoms.order.service;

import com.edoms.order.dto.InventoryUpdatedEvent;
import com.edoms.order.dto.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Service class for consuming events from Kafka topics.
 * Listens to payment and inventory events to coordinate order status.
 */
@Service
@Slf4j
public class KafkaConsumerService {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public KafkaConsumerService(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    /**
     * Listens to payment-events topic and processes payment events.
     * Updates order status based on payment success/failure.
     * 
     * @param message the payment event message
     */
    @KafkaListener(topics = "payment-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEvent(String message) {
        try {
            log.info("Received payment event: {}", message);
            PaymentProcessedEvent event = objectMapper.readValue(message, PaymentProcessedEvent.class);
            
            orderService.handlePaymentEvent(event);
            
            log.info("Successfully processed payment event for Order ID={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Error processing payment event: {}", e.getMessage(), e);
        }
    }

    /**
     * Listens to inventory-events topic and processes inventory events.
     * Updates order status based on inventory availability.
     * 
     * @param message the inventory event message
     */
    @KafkaListener(topics = "inventory-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeInventoryEvent(String message) {
        try {
            log.info("Received inventory event: {}", message);
            InventoryUpdatedEvent event = objectMapper.readValue(message, InventoryUpdatedEvent.class);
            
            orderService.handleInventoryEvent(event);
            
            log.info("Successfully processed inventory event for Order ID={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Error processing inventory event: {}", e.getMessage(), e);
        }
    }
}
