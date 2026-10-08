package com.edoms.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Event DTO published after processing payment.
 * Sent to order-service via Kafka.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentProcessedEvent implements Serializable {
    
    private Long orderId;
    private Long paymentId;
    private boolean paymentSuccessful;
    private String paymentStatus;
    private BigDecimal amount;
    
    @Builder.Default
    private String eventType = "PAYMENT_PROCESSED";
}
