package com.edoms.order.service;

import com.edoms.order.dto.InventoryUpdatedEvent;
import com.edoms.order.dto.OrderCreatedEvent;
import com.edoms.order.dto.PaymentProcessedEvent;
import com.edoms.order.model.Order;
import com.edoms.order.model.OrderStatus;
import com.edoms.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service class for order business logic and Saga orchestration.
 * Coordinates order state based on payment and inventory events.
 */
@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaProducerService kafkaProducerService;
    
    // Track payment and inventory status for each order (Saga state)
    private final ConcurrentHashMap<Long, Boolean> paymentStatus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> inventoryStatus = new ConcurrentHashMap<>();

    public OrderService(OrderRepository orderRepository, KafkaProducerService kafkaProducerService) {
        this.orderRepository = orderRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    /**
     * Creates a new order and publishes OrderCreatedEvent to Kafka.
     * 
     * @param order the order to create
     * @return the created order with CREATED status
     */
    @Transactional
    public Order createOrder(Order order) {
        log.info("Creating order: Product={}, Quantity={}, Price={}", 
                order.getProductName(), order.getQuantity(), order.getPrice());
        
        // Set initial status
        order.setStatus(OrderStatus.CREATED);
        
        // Save to database
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved with ID={}", savedOrder.getId());
        
        // Initialize saga state
        paymentStatus.put(savedOrder.getId(), false);
        inventoryStatus.put(savedOrder.getId(), false);
        
        // Publish OrderCreatedEvent
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .productName(savedOrder.getProductName())
                .quantity(savedOrder.getQuantity())
                .price(savedOrder.getPrice())
                .status(savedOrder.getStatus())
                .build();
        
        kafkaProducerService.sendOrderCreatedEvent(event);
        
        return savedOrder;
    }

    /**
     * Retrieves an order by ID.
     * 
     * @param id the order ID
     * @return Optional containing the order if found
     */
    public Optional<Order> getOrderById(Long id) {
        log.info("Retrieving order with ID={}", id);
        return orderRepository.findById(id);
    }

    /**
     * Handles payment processed events from payment-service.
     * Updates order status based on payment success/failure.
     * 
     * @param event the payment processed event
     */
    @Transactional
    public void handlePaymentEvent(PaymentProcessedEvent event) {
        log.info("Handling payment event for Order ID={}, Status={}", 
                event.getOrderId(), event.getPaymentStatus());
        
        Optional<Order> orderOpt = orderRepository.findById(event.getOrderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found with ID={}", event.getOrderId());
            return;
        }
        
        Order order = orderOpt.get();
        
        if (event.isPaymentSuccessful()) {
            paymentStatus.put(event.getOrderId(), true);
            log.info("Payment successful for Order ID={}", event.getOrderId());
            
            // Update to PROCESSING status
            order.setStatus(OrderStatus.PROCESSING);
            orderRepository.save(order);
            
            // Check if both payment and inventory are ready
            checkAndCompleteOrder(event.getOrderId());
        } else {
            log.error("Payment failed for Order ID={}", event.getOrderId());
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            
            // Clean up saga state
            cleanupSagaState(event.getOrderId());
        }
    }

    /**
     * Handles inventory updated events from inventory-service.
     * Updates order status based on inventory availability.
     * 
     * @param event the inventory updated event
     */
    @Transactional
    public void handleInventoryEvent(InventoryUpdatedEvent event) {
        log.info("Handling inventory event for Order ID={}, Status={}", 
                event.getOrderId(), event.getInventoryStatus());
        
        Optional<Order> orderOpt = orderRepository.findById(event.getOrderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found with ID={}", event.getOrderId());
            return;
        }
        
        Order order = orderOpt.get();
        
        if (event.isInventoryAvailable() && "RESERVED".equals(event.getInventoryStatus())) {
            inventoryStatus.put(event.getOrderId(), true);
            log.info("Inventory reserved for Order ID={}", event.getOrderId());
            
            // Check if both payment and inventory are ready
            checkAndCompleteOrder(event.getOrderId());
        } else {
            log.error("Inventory insufficient for Order ID={}", event.getOrderId());
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            
            // Clean up saga state
            cleanupSagaState(event.getOrderId());
        }
    }

    /**
     * Checks if both payment and inventory are successful and completes the order.
     * This implements the Saga pattern coordination logic.
     * 
     * @param orderId the order ID to check
     */
    private void checkAndCompleteOrder(Long orderId) {
        Boolean paymentSuccess = paymentStatus.get(orderId);
        Boolean inventorySuccess = inventoryStatus.get(orderId);
        
        if (Boolean.TRUE.equals(paymentSuccess) && Boolean.TRUE.equals(inventorySuccess)) {
            log.info("Both payment and inventory confirmed for Order ID={}. Completing order.", orderId);
            
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                order.setStatus(OrderStatus.COMPLETED);
                orderRepository.save(order);
                
                log.info("Order ID={} marked as COMPLETED", orderId);
                
                // Clean up saga state
                cleanupSagaState(orderId);
            }
        } else {
            log.debug("Waiting for confirmation - Order ID={}, Payment={}, Inventory={}", 
                    orderId, paymentSuccess, inventorySuccess);
        }
    }

    /**
     * Cleans up the saga state for completed or failed orders.
     * 
     * @param orderId the order ID to clean up
     */
    private void cleanupSagaState(Long orderId) {
        paymentStatus.remove(orderId);
        inventoryStatus.remove(orderId);
        log.debug("Saga state cleaned up for Order ID={}", orderId);
    }
}
