package com.edoms.order.service;

import com.edoms.order.dto.*;
import com.edoms.order.model.*;
import com.edoms.order.repository.OrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final KafkaProducerService kafkaProducerService;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(Order.builder()
                .productName(request.productName().trim()).quantity(request.quantity())
                .price(request.price()).status(OrderStatus.CREATED).build());
        kafkaProducerService.sendOrderCreatedEvent(toEvent(order, "ORDER_CREATED"));
        return order;
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public void handlePaymentEvent(PaymentProcessedEvent event) {
        if (event == null || !"PAYMENT_PROCESSED".equals(event.getEventType())
                || event.getOrderId() == null || event.getOrderId() <= 0
                || !("SUCCESS".equals(event.getPaymentStatus()) && event.isPaymentSuccessful()
                  || "FAILED".equals(event.getPaymentStatus()) && !event.isPaymentSuccessful())) {
            throw new IllegalArgumentException("Invalid payment result");
        }
        Order order = lockOrder(event.getOrderId());
        if (isTerminal(order) || order.getPaymentSuccessful() != null) return;
        order.setPaymentSuccessful(event.isPaymentSuccessful());
        updateStatus(order);
    }

    @Transactional
    public void handleInventoryEvent(InventoryUpdatedEvent event) {
        if (event == null || !"INVENTORY_UPDATED".equals(event.getEventType())
                || event.getOrderId() == null || event.getOrderId() <= 0
                || !("RESERVED".equals(event.getInventoryStatus()) && event.isInventoryAvailable()
                  || "INSUFFICIENT".equals(event.getInventoryStatus()) && !event.isInventoryAvailable())) {
            throw new IllegalArgumentException("Invalid inventory result");
        }
        Order order = lockOrder(event.getOrderId());
        if (isTerminal(order) || order.getInventoryAvailable() != null) return;
        order.setInventoryAvailable(event.isInventoryAvailable());
        updateStatus(order);
    }

    private Order lockOrder(Long id) {
        // A missing order may be temporarily unavailable; let Kafka retry.
        return orderRepository.findLockedById(id)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + id));
    }

    private boolean isTerminal(Order order) {
        return order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.FAILED;
    }

    private void updateStatus(Order order) {
        if (Boolean.FALSE.equals(order.getPaymentSuccessful())
                || Boolean.FALSE.equals(order.getInventoryAvailable())) {
            order.setStatus(OrderStatus.FAILED);
            // Same topic/key as creation preserves command order for both participants.
            kafkaProducerService.sendOrderCreatedEvent(toEvent(order, "ORDER_CANCELLED"));
        } else if (Boolean.TRUE.equals(order.getPaymentSuccessful())
                && Boolean.TRUE.equals(order.getInventoryAvailable())) {
            order.setStatus(OrderStatus.COMPLETED);
        } else {
            order.setStatus(OrderStatus.PROCESSING);
        }
        orderRepository.save(order);
    }

    private OrderCreatedEvent toEvent(Order order, String type) {
        return OrderCreatedEvent.builder().orderId(order.getId()).productName(order.getProductName())
                .quantity(order.getQuantity()).price(order.getPrice()).status(order.getStatus())
                .eventType(type).build();
    }
}
