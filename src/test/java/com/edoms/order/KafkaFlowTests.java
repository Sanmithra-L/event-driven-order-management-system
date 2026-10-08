package com.edoms.order;

import com.edoms.order.dto.CreateOrderRequest;
import com.edoms.order.model.OrderStatus;
import com.edoms.order.repository.OrderRepository;
import com.edoms.order.service.OrderService;
import com.edoms.order.outbox.OutboxPublisher;
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
        "spring.datasource.url=jdbc:h2:mem:orderwire;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.kafka.listener.auto-startup=true",
        "edoms.outbox.enabled=true", "edoms.outbox.poll-interval-ms=100"})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "payment-events", "inventory-events", "payment-events.DLT"})
@DirtiesContext
class KafkaFlowTests {
    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired KafkaTemplate<String, Object> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ObjectMapper mapper;
    @Autowired OutboxPublisher publisher;

    @Test void jsonEventsRoundTripThroughRealKafkaAndInvalidEventsAreRetained() throws Exception {
        Map<String, Object> props = KafkaTestUtils.consumerProps("order-observer", "false", broker);
        try (Consumer<String, String> observer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
             Consumer<String, String> deadLetters = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps("order-dead-letters", "false", broker),
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(observer, "order-events");
            broker.consumeFromAnEmbeddedTopic(deadLetters, "payment-events.DLT");

        Long id = service.createOrder(new CreateOrderRequest("Laptop", 1, new BigDecimal("10.10"))).getId();
        String created = KafkaTestUtils.getSingleRecord(observer, "order-events").value();
        assertThat(mapper.readTree(created).get("orderId").asLong()).isEqualTo(id);
        kafka.send("inventory-events", id.toString(), mapper.readTree(
                "{\"orderId\":" + id + ",\"inventoryAvailable\":true,\"inventoryStatus\":\"RESERVED\"}")).get();
        kafka.send("payment-events", id.toString(), mapper.readTree(
                "{\"orderId\":" + id + ",\"paymentSuccessful\":true,\"paymentStatus\":\"SUCCESS\"}")).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED));
        // A duplicate must not regress a completed order.
        kafka.send("payment-events", id.toString(), mapper.readTree(
                "{\"orderId\":" + id + ",\"paymentSuccessful\":true,\"paymentStatus\":\"SUCCESS\"}")).get();

            kafka.send("payment-events", "invalid", Map.of("unexpected", "invalid event")).get();
            String deadLetter = KafkaTestUtils.getSingleRecord(deadLetters, "payment-events.DLT").value();
            assertThat(mapper.readTree(deadLetter).get("payload").asText()).contains("unexpected");
        }
    }
}
