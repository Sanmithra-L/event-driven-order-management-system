package com.edoms.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event DTO received when an order is created.
 * Consumed from order-service via Kafka.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent implements Serializable {
    
    private Long orderId;
    private String productName;
    private int quantity;
    private double price;
    private String status;
    
    @Builder.Default
    private String eventType = "ORDER_CREATED";
}
