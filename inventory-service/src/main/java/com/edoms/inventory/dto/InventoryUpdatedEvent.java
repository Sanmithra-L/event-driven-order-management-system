package com.edoms.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event DTO published after updating inventory.
 * Sent to order-service via Kafka.
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
