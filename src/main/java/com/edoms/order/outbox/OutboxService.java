package com.edoms.order.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxRepository repository;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String topic, Long orderId, Object event) {
        try {
            repository.save(new OutboxEvent(topic, orderId.toString(), mapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize event", exception);
        }
    }
}
