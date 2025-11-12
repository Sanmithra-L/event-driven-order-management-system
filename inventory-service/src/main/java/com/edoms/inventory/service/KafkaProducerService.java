package com.edoms.inventory.service;

import com.edoms.inventory.dto.InventoryUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service class for producing inventory events to Kafka topics.
 * Handles sending inventory updated messages to the configured Kafka broker.
 */
@Service
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends an InventoryUpdatedEvent to the inventory-events topic.
     * This is consumed by order-service to update order status.
     * 
     * @param event the InventoryUpdatedEvent to send
     */
    public void sendInventoryUpdatedEvent(InventoryUpdatedEvent event) {
        log.info("Sending InventoryUpdatedEvent to topic '{}': Order ID={}, Inventory Status={}", 
                INVENTORY_EVENTS_TOPIC, event.getOrderId(), event.getInventoryStatus());
        
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(INVENTORY_EVENTS_TOPIC, 
                        event.getOrderId() != null ? event.getOrderId().toString() : "null", 
                        event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully sent InventoryUpdatedEvent: Order ID={}, Offset={}, Partition={}", 
                        event.getOrderId(), 
                        result.getRecordMetadata().offset(), 
                        result.getRecordMetadata().partition());
            } else {
                log.error("Failed to send InventoryUpdatedEvent: Order ID={}, Error={}", 
                        event.getOrderId(), ex.getMessage(), ex);
            }
        });
    }
}
