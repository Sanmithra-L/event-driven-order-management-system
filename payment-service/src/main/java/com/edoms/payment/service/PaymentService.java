package com.edoms.payment.service;

import com.edoms.payment.dto.OrderCreatedEvent;
import com.edoms.payment.dto.PaymentProcessedEvent;
import com.edoms.payment.model.Payment;
import com.edoms.payment.model.PaymentStatus;
import com.edoms.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service class for payment processing business logic.
 * Handles payment processing and event publishing.
 */
@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaProducerService kafkaProducerService;

    public PaymentService(PaymentRepository paymentRepository, KafkaProducerService kafkaProducerService) {
        this.paymentRepository = paymentRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    /**
     * Processes payment for an order.
     * Simulates payment processing logic and publishes result to Kafka.
     * 
     * @param orderEvent the order created event
     */
    @Transactional
    public void processPayment(OrderCreatedEvent orderEvent) {
        log.info("Processing payment for Order ID={}, Amount={}", 
                orderEvent.getOrderId(), orderEvent.getPrice());
        
        // Create payment record
        Payment payment = Payment.builder()
                .orderId(orderEvent.getOrderId())
                .amount(orderEvent.getPrice())
                .paymentStatus(PaymentStatus.PENDING)
                .transactionId(UUID.randomUUID().toString())
                .build();
        
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment record created with ID={}", savedPayment.getId());
        
        // Simulate payment processing logic
        boolean paymentSuccess = simulatePaymentProcessing(orderEvent.getPrice());
        
        // Update payment status
        savedPayment.setPaymentStatus(paymentSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepository.save(savedPayment);
        
        log.info("Payment processing completed for Order ID={}, Status={}", 
                orderEvent.getOrderId(), savedPayment.getPaymentStatus());
        
        // Publish PaymentProcessedEvent
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .orderId(orderEvent.getOrderId())
                .paymentId(savedPayment.getId())
                .paymentSuccessful(paymentSuccess)
                .paymentStatus(savedPayment.getPaymentStatus().name())
                .amount(savedPayment.getAmount())
                .build();
        
        kafkaProducerService.sendPaymentProcessedEvent(event);
    }

    /**
     * Simulates payment processing logic.
     * In production, this would integrate with payment gateway APIs.
     * Mock logic: Payments above 10000 fail, others succeed.
     * 
     * @param amount the payment amount
     * @return true if payment successful, false otherwise
     */
    private boolean simulatePaymentProcessing(double amount) {
        try {
            // Simulate processing delay
            Thread.sleep(1000);
            
            // Mock logic: fail payments above 10000
            if (amount > 10000) {
                log.warn("Payment amount {} exceeds limit. Payment failed.", amount);
                return false;
            }
            
            log.info("Payment processing successful for amount {}", amount);
            return true;
            
        } catch (InterruptedException e) {
            log.error("Payment processing interrupted", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
