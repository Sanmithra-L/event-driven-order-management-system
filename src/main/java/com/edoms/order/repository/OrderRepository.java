package com.edoms.order.repository;

import com.edoms.order.model.Order;
import com.edoms.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository interface for Order entity.
 * Provides CRUD operations and custom query methods for order management.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findLockedById(@Param("id") Long id);
    
    /**
     * Find all orders by status.
     * 
     * @param status the order status to filter by
     * @return list of orders with the specified status
     */
    List<Order> findByStatus(OrderStatus status);
    
    /**
     * Find orders by product name.
     * 
     * @param productName the product name to search for
     * @return list of orders for the specified product
     */
    List<Order> findByProductName(String productName);
}
