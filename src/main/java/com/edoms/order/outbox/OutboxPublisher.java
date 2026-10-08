package com.edoms.order.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxRepository repository;
    private final ObjectMapper mapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelayString = "${edoms.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        // Keep rows until the broker acknowledges. A crash can replay a row;
        // consumers use durable per-order state to make those replays harmless.
        for (OutboxEvent event : repository.findTop25ByOrderByIdAsc()) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), mapper.readTree(event.getPayload()))
                        .get(10, TimeUnit.SECONDS);
                repository.delete(event);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Outbox publishing interrupted", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("Outbox publishing failed; event retained", exception);
            }
        }
    }
}
