package com.edoms.payment.service;

import com.edoms.payment.dto.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service class for producing payment events to Kafka topics.
 * Handles sending payment processed messages to the configured Kafka broker.
 */
@Service
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends a PaymentProcessedEvent to the payment-events topic.
     * This is consumed by order-service to update order status.
     * 
     * @param event the PaymentProcessedEvent to send
     */
    public void sendPaymentProcessedEvent(PaymentProcessedEvent event) {
        log.info("Sending PaymentProcessedEvent to topic '{}': Order ID={}, Payment Status={}", 
                PAYMENT_EVENTS_TOPIC, event.getOrderId(), event.getPaymentStatus());
        
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, 
                        event.getOrderId() != null ? event.getOrderId().toString() : "null", 
                        event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully sent PaymentProcessedEvent: Order ID={}, Offset={}, Partition={}", 
                        event.getOrderId(), 
                        result.getRecordMetadata().offset(), 
                        result.getRecordMetadata().partition());
            } else {
                log.error("Failed to send PaymentProcessedEvent: Order ID={}, Error={}", 
                        event.getOrderId(), ex.getMessage(), ex);
            }
        });
    }
}
