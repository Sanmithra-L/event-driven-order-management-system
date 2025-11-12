package com.edoms.order.dto;

import com.edoms.order.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event DTO published when an order is created.
 * Used for inter-service communication via Kafka.
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
    private OrderStatus status;
    
    @Builder.Default
    private String eventType = "ORDER_CREATED";
}
