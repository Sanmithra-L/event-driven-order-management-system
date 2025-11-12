package com.edoms.inventory.config;

import com.edoms.inventory.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Data initializer to populate sample inventory data on startup.
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final InventoryService inventoryService;

    public DataInitializer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public void run(String... args) {
        log.info("Initializing inventory data...");
        inventoryService.initializeInventory();
    }
}
