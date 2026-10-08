package com.edoms.inventory;

import com.edoms.inventory.model.ReservationStatus;
import com.edoms.inventory.repository.ReservationRepository;
import com.edoms.inventory.outbox.OutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inventorywire;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.kafka.listener.auto-startup=true",
        "edoms.outbox.enabled=true", "edoms.outbox.poll-interval-ms=100"})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "payment-events", "inventory-events", "order-events.DLT"})
@DirtiesContext
class KafkaFlowTests {
    @Autowired ReservationRepository reservations;
    @Autowired KafkaTemplate<String, Object> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ObjectMapper mapper;
    @Autowired OutboxPublisher publisher;

    @Test void jsonEventsRoundTripThroughRealKafkaAndInvalidEventsAreRetained() throws Exception {
        Map<String, Object> props = KafkaTestUtils.consumerProps("inventory-observer", "false", broker);
        try (Consumer<String, String> observer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
             Consumer<String, String> deadLetters = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps("inventory-dead-letters", "false", broker),
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(observer, "inventory-events");
            broker.consumeFromAnEmbeddedTopic(deadLetters, "order-events.DLT");

        var created = mapper.readTree(
                "{\"orderId\":42,\"productName\":\"Laptop\",\"quantity\":2,\"price\":20.10,"
                + "\"status\":\"CREATED\",\"eventType\":\"ORDER_CREATED\"}");
        kafka.send("order-events", "42", created).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(reservations.count()).isEqualTo(1));
        String result = KafkaTestUtils.getSingleRecord(observer, "inventory-events").value();
        assertThat(mapper.readTree(result).get("orderId").asLong()).isEqualTo(42);
        assertThat(mapper.readTree(result).get("inventoryAvailable").asBoolean()).isTrue();
        var cancelled = created.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) cancelled).put("eventType", "ORDER_CANCELLED");
        kafka.send("order-events", "42", cancelled).get();
        kafka.send("order-events", "42", cancelled).get();
        kafka.send("order-events", "42", created).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(reservations.findById(42L).orElseThrow().getStatus())
                        .isEqualTo(ReservationStatus.RELEASED));

            kafka.send("order-events", "invalid", Map.of("unexpected", "invalid event")).get();
            String deadLetter = KafkaTestUtils.getSingleRecord(deadLetters, "order-events.DLT").value();
            assertThat(mapper.readTree(deadLetter).get("payload").asText()).contains("unexpected");
        }
    }
}
