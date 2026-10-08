package com.edoms.inventory.config;

import com.edoms.inventory.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Data initializer to populate sample inventory data on startup.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "edoms.inventory.seed-data", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements SmartInitializingSingleton {

    private final InventoryService inventoryService;

    public DataInitializer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.info("Initializing inventory data...");
        inventoryService.initializeInventory();
    }
}
