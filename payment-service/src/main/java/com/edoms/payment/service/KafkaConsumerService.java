package com.edoms.payment.service;

import com.edoms.payment.dto.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Service class for consuming events from Kafka topics.
 * Listens to order events and triggers payment processing.
 */
@Service
@Slf4j
public class KafkaConsumerService {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public KafkaConsumerService(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    /**
     * Listens to order-events topic and processes order created events.
     * Triggers payment processing for new orders.
     * 
     * @param message the order event message
     */
    @KafkaListener(topics = "order-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeOrderEvent(String message) {
        try {
            log.info("Received order event: {}", message);
            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
            
            paymentService.processPayment(event);
            
            log.info("Successfully processed order event for Order ID={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Error processing order event: {}", e.getMessage(), e);
        }
    }
}
