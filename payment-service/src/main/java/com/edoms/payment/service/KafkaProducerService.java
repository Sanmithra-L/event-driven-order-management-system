package com.edoms.payment.service;

import com.edoms.payment.dto.PaymentProcessedEvent;
import com.edoms.payment.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final OutboxService outbox;

    public void sendPaymentProcessedEvent(PaymentProcessedEvent event) {
        outbox.enqueue("payment-events", event.getOrderId(), event);
    }
}
