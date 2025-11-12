package com.edoms.inventory.service;

import com.edoms.inventory.dto.InventoryUpdatedEvent;
import com.edoms.inventory.dto.OrderCreatedEvent;
import com.edoms.inventory.model.Inventory;
import com.edoms.inventory.repository.InventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service class for inventory management business logic.
 * Handles inventory reservation/release and event publishing.
 */
@Service
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final KafkaProducerService kafkaProducerService;

    public InventoryService(InventoryRepository inventoryRepository, KafkaProducerService kafkaProducerService) {
        this.inventoryRepository = inventoryRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    /**
     * Reserves inventory for an order.
     * Checks availability and updates stock, then publishes result to Kafka.
     * 
     * @param orderEvent the order created event
     */
    @Transactional
    public void reserveInventory(OrderCreatedEvent orderEvent) {
        log.info("Reserving inventory for Order ID={}, Product={}, Quantity={}", 
                orderEvent.getOrderId(), orderEvent.getProductName(), orderEvent.getQuantity());
        
        Optional<Inventory> inventoryOpt = inventoryRepository.findByProductName(orderEvent.getProductName());
        
        boolean inventoryAvailable = false;
        String inventoryStatus = "INSUFFICIENT";
        
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            
            if (inventory.getAvailableStock() >= orderEvent.getQuantity()) {
                // Sufficient stock available - reserve it
                inventory.setAvailableStock(inventory.getAvailableStock() - orderEvent.getQuantity());
                inventory.setReservedStock(inventory.getReservedStock() + orderEvent.getQuantity());
                inventoryRepository.save(inventory);
                
                inventoryAvailable = true;
                inventoryStatus = "RESERVED";
                
                log.info("Inventory reserved for Order ID={}, Product={}, Quantity={}, Remaining stock={}", 
                        orderEvent.getOrderId(), orderEvent.getProductName(), 
                        orderEvent.getQuantity(), inventory.getAvailableStock());
            } else {
                log.warn("Insufficient inventory for Order ID={}, Product={}, Required={}, Available={}", 
                        orderEvent.getOrderId(), orderEvent.getProductName(), 
                        orderEvent.getQuantity(), inventory.getAvailableStock());
            }
        } else {
            log.warn("Product not found in inventory: {}", orderEvent.getProductName());
        }
        
        // Publish InventoryUpdatedEvent
        InventoryUpdatedEvent event = InventoryUpdatedEvent.builder()
                .orderId(orderEvent.getOrderId())
                .productName(orderEvent.getProductName())
                .quantity(orderEvent.getQuantity())
                .inventoryAvailable(inventoryAvailable)
                .inventoryStatus(inventoryStatus)
                .build();
        
        kafkaProducerService.sendInventoryUpdatedEvent(event);
    }

    /**
     * Releases reserved inventory (compensation action for failed orders).
     * 
     * @param orderEvent the order event for which to release inventory
     */
    @Transactional
    public void releaseInventory(OrderCreatedEvent orderEvent) {
        log.info("Releasing inventory for Order ID={}, Product={}, Quantity={}", 
                orderEvent.getOrderId(), orderEvent.getProductName(), orderEvent.getQuantity());
        
        Optional<Inventory> inventoryOpt = inventoryRepository.findByProductName(orderEvent.getProductName());
        
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            
            // Release reserved stock back to available
            inventory.setAvailableStock(inventory.getAvailableStock() + orderEvent.getQuantity());
            inventory.setReservedStock(Math.max(0, inventory.getReservedStock() - orderEvent.getQuantity()));
            inventoryRepository.save(inventory);
            
            log.info("Inventory released for Order ID={}, Product={}, Quantity={}", 
                    orderEvent.getOrderId(), orderEvent.getProductName(), orderEvent.getQuantity());
            
            // Publish event
            InventoryUpdatedEvent event = InventoryUpdatedEvent.builder()
                    .orderId(orderEvent.getOrderId())
                    .productName(orderEvent.getProductName())
                    .quantity(orderEvent.getQuantity())
                    .inventoryAvailable(true)
                    .inventoryStatus("RELEASED")
                    .build();
            
            kafkaProducerService.sendInventoryUpdatedEvent(event);
        } else {
            log.warn("Product not found in inventory for release: {}", orderEvent.getProductName());
        }
    }

    /**
     * Initializes sample inventory data.
     * In production, this would be loaded from database or admin interface.
     */
    @Transactional
    public void initializeInventory() {
        if (inventoryRepository.count() == 0) {
            log.info("Initializing sample inventory data...");
            
            inventoryRepository.save(Inventory.builder()
                    .productName("Laptop")
                    .availableStock(50)
                    .reservedStock(0)
                    .build());
            
            inventoryRepository.save(Inventory.builder()
                    .productName("Mouse")
                    .availableStock(100)
                    .reservedStock(0)
                    .build());
            
            inventoryRepository.save(Inventory.builder()
                    .productName("Keyboard")
                    .availableStock(75)
                    .reservedStock(0)
                    .build());
            
            log.info("Sample inventory data initialized");
        }
    }
}
