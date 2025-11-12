package com.edoms.order.service;

import com.edoms.order.dto.OrderCreatedEvent;
import com.edoms.order.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service class for producing Order events to Kafka topics.
 * Handles sending order messages to the configured Kafka broker.
 */
@Service
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.template.default-topic}")
    private String orderEventsTopic;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends an order event to the Kafka 'order-events' topic.
     * 
     * @param order the order to send
     */
    public void sendOrderEvent(Order order) {
        log.info("Sending order event to topic '{}': Order ID={}, Product={}, Status={}", 
                orderEventsTopic, order.getId(), order.getProductName(), order.getStatus());
        
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(orderEventsTopic, order.getId() != null ? order.getId().toString() : "null", order);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully sent order event: Order ID={}, Offset={}, Partition={}", 
                        order.getId(), 
                        result.getRecordMetadata().offset(), 
                        result.getRecordMetadata().partition());
            } else {
                log.error("Failed to send order event: Order ID={}, Error={}", 
                        order.getId(), ex.getMessage(), ex);
            }
        });
    }

    /**
     * Sends an OrderCreatedEvent to the Kafka 'order-events' topic.
     * This is consumed by payment-service and inventory-service.
     * 
     * @param event the OrderCreatedEvent to send
     */
    public void sendOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("Sending OrderCreatedEvent to topic '{}': Order ID={}, Product={}, Status={}", 
                orderEventsTopic, event.getOrderId(), event.getProductName(), event.getStatus());
        
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(orderEventsTopic, 
                        event.getOrderId() != null ? event.getOrderId().toString() : "null", 
                        event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully sent OrderCreatedEvent: Order ID={}, Offset={}, Partition={}", 
                        event.getOrderId(), 
                        result.getRecordMetadata().offset(), 
                        result.getRecordMetadata().partition());
            } else {
                log.error("Failed to send OrderCreatedEvent: Order ID={}, Error={}", 
                        event.getOrderId(), ex.getMessage(), ex);
            }
        });
    }
}
