package com.edoms.payment.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Retry infrastructure failures without acknowledging the failed record.
        // Invalid input is retained in a dead-letter envelope for operator inspection.
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            try {
                kafkaTemplate.send(record.topic() + ".DLT",
                        record.key() == null ? null : record.key().toString(),
                        Map.of("payload", record.value() == null ? "" : record.value(),
                               "error", exception.toString()))
                        .get(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Dead-letter publishing interrupted", interrupted);
            } catch (Exception failed) {
                throw new IllegalStateException("Dead-letter publishing failed", failed);
            }
        }, new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }
}
