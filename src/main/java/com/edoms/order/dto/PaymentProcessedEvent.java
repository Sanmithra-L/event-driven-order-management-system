package com.edoms.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Event DTO published by payment-service after processing payment.
 * Consumed by order-service to update order status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentProcessedEvent implements Serializable {
    
    private Long orderId;
    private Long paymentId;
    private boolean paymentSuccessful;
    private String paymentStatus; // SUCCESS, FAILED
    private BigDecimal amount;
    
    @Builder.Default
    private String eventType = "PAYMENT_PROCESSED";
}
