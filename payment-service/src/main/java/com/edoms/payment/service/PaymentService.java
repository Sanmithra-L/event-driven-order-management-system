package com.edoms.payment.service;

import com.edoms.payment.dto.*;
import com.edoms.payment.model.*;
import com.edoms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final BigDecimal PAYMENT_LIMIT = new BigDecimal("10000.00");
    private final PaymentRepository paymentRepository;
    private final KafkaProducerService kafkaProducerService;

    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        OrderEventValidation.validate(event);
        // The unique order ID also protects simultaneous first deliveries.
        // A racing insert rolls back and Kafka retries against the saved record.
        if (paymentRepository.findByOrderId(event.getOrderId()).isPresent()) return;
        boolean success = event.getPrice().compareTo(PAYMENT_LIMIT) <= 0;
        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(event.getOrderId()).amount(event.getPrice())
                .transactionId(UUID.randomUUID().toString())
                .paymentStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED).build());
        kafkaProducerService.sendPaymentProcessedEvent(PaymentProcessedEvent.builder()
                .orderId(event.getOrderId()).paymentId(payment.getId())
                .amount(payment.getAmount()).paymentSuccessful(success)
                .paymentStatus(payment.getPaymentStatus().name()).build());
    }

    @Transactional
    public void cancelPayment(OrderCreatedEvent event) {
        OrderEventValidation.validate(event);
        Payment payment = paymentRepository.findByOrderId(event.getOrderId()).orElseGet(() ->
                Payment.builder().orderId(event.getOrderId()).amount(event.getPrice())
                        .transactionId(UUID.randomUUID().toString())
                        .paymentStatus(PaymentStatus.CANCELLED).build());
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            // This service simulates payments; a real gateway needs an idempotent refund API.
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
        } else if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
        }
        paymentRepository.save(payment);
    }
}
