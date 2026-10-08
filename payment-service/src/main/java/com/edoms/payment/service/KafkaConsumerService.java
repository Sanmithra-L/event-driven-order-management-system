package com.edoms.payment.service;

import com.edoms.payment.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    private final PaymentService service;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events")
    public void consumeOrderEvent(String message) throws JsonProcessingException {
        OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
        OrderEventValidation.validate(event);
        if ("ORDER_CANCELLED".equals(event.getEventType())) {
            service.cancelPayment(event);
        } else {
            service.processPayment(event);
        }
    }
}
