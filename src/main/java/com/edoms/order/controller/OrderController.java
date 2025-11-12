package com.edoms.order.controller;

import com.edoms.order.model.Order;
import com.edoms.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing orders.
 * Provides endpoints for creating and retrieving orders.
 */
@RestController
@RequestMapping("/orders")
@Slf4j
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order with CREATED status and publishes it to Kafka.
     * 
     * @param order the order to create (without ID and status)
     * @return ResponseEntity with the created order
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        log.info("Received request to create order: Product={}, Quantity={}, Price={}", 
                order.getProductName(), order.getQuantity(), order.getPrice());
        
        // Create order using OrderService (handles status, persistence, and Kafka)
        Order createdOrder = orderService.createOrder(order);
        
        return new ResponseEntity<>(createdOrder, HttpStatus.CREATED);
    }

    /**
     * Retrieves an order by its ID.
     * 
     * @param id the order ID
     * @return ResponseEntity with the order if found, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        log.info("Received request to retrieve order with ID={}", id);
        
        return orderService.getOrderById(id)
                .map(order -> {
                    log.info("Order found: ID={}, Status={}", order.getId(), order.getStatus());
                    return new ResponseEntity<>(order, HttpStatus.OK);
                })
                .orElseGet(() -> {
                    log.warn("Order not found with ID={}", id);
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                });
    }
}
