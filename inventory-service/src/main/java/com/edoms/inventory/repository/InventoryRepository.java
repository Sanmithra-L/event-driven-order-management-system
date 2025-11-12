package com.edoms.inventory.repository;

import com.edoms.inventory.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository interface for Inventory entity.
 * Provides CRUD operations and custom query methods for inventory management.
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    
    /**
     * Find inventory by product name.
     * 
     * @param productName the product name
     * @return Optional containing the inventory if found
     */
    Optional<Inventory> findByProductName(String productName);
}
