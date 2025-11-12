package com.edoms.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event DTO published by inventory-service after updating stock.
 * Consumed by order-service to update order status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryUpdatedEvent implements Serializable {
    
    private Long orderId;
    private String productName;
    private int quantity;
    private boolean inventoryAvailable;
    private String inventoryStatus; // RESERVED, INSUFFICIENT, RELEASED
    
    @Builder.Default
    private String eventType = "INVENTORY_UPDATED";
}
