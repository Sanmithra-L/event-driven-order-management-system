package com.edoms.order;

import com.edoms.order.dto.*;
import com.edoms.order.model.*;
import com.edoms.order.outbox.*;
import com.edoms.order.repository.OrderRepository;
import com.edoms.order.service.OrderService;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderServiceApplicationTests {
    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired OutboxRepository outbox;
    @Autowired OutboxPublisher publisher;
    @Autowired MockMvc mvc;
    @MockitoBean KafkaTemplate<String, Object> kafka;

    @BeforeEach void resetData() { outbox.deleteAll(); orders.deleteAll(); }

    Order create() { return service.createOrder(new CreateOrderRequest("Laptop", 2, new BigDecimal("2400.00"))); }
    PaymentProcessedEvent payment(Long id, boolean success) {
        return PaymentProcessedEvent.builder().orderId(id).paymentSuccessful(success)
                .paymentStatus(success ? "SUCCESS" : "FAILED").build();
    }
    InventoryUpdatedEvent inventory(Long id, boolean success) {
        return InventoryUpdatedEvent.builder().orderId(id).inventoryAvailable(success)
                .inventoryStatus(success ? "RESERVED" : "INSUFFICIENT").build();
    }

    @Test void persistsProgressAcrossTransactionsAndIgnoresDuplicates() {
        Long id = create().getId();
        service.handleInventoryEvent(inventory(id, true));
        assertThat(orders.findById(id).orElseThrow().getInventoryAvailable()).isTrue();
        service.handlePaymentEvent(payment(id, true));
        service.handlePaymentEvent(payment(id, true));
        service.handleInventoryEvent(inventory(id, true));
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test void completesWhenPaymentArrivesFirst() {
        Long id = create().getId();
        service.handlePaymentEvent(payment(id, true));
        service.handleInventoryEvent(inventory(id, true));
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test void failureIsTerminalAndEmitsOneCancellation() {
        Long id = create().getId();
        service.handleInventoryEvent(inventory(id, false));
        service.handlePaymentEvent(payment(id, true));
        service.handleInventoryEvent(inventory(id, false));
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(outbox.findAll()).hasSize(2)
                .anySatisfy(e -> assertThat(e.getPayload()).contains("ORDER_CANCELLED"));
    }

    @Test void paymentFailureCancelsAfterReservation() {
        Long id = create().getId();
        service.handleInventoryEvent(inventory(id, true));
        service.handlePaymentEvent(payment(id, false));
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(outbox.count()).isEqualTo(2);
    }

    @Test void simultaneousResultsDoNotLoseProgress() {
        Long id = create().getId();
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> service.handlePaymentEvent(payment(id, true))),
                CompletableFuture.runAsync(() -> service.handleInventoryEvent(inventory(id, true)))).join();
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test void rejectsInconsistentResults() {
        Long id = create().getId();
        PaymentProcessedEvent event = payment(id, true);
        event.setPaymentStatus("FAILED");
        assertThatThrownBy(() -> service.handlePaymentEvent(event)).isInstanceOf(IllegalArgumentException.class);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test void missingOrderIsRetriable() {
        assertThatThrownBy(() -> service.handlePaymentEvent(payment(Long.MAX_VALUE, true)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void retainsOutboxOnBrokerFailureAndDeletesAfterAcknowledgement() {
        create();
        when(kafka.send(anyString(), anyString(), any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        assertThatThrownBy(() -> publisher.publishPending()).isInstanceOf(IllegalStateException.class);
        assertThat(outbox.count()).isEqualTo(1);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishPending();
        assertThat(outbox.count()).isZero();
    }

    @Test void createsAndRetrievesValidatedOrder() throws Exception {
        mvc.perform(post("/orders").contentType("application/json")
                .content("{\"productName\":\"Laptop\",\"quantity\":2,\"price\":2400.10}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.price").value(2400.10))
                .andExpect(jsonPath("$.paymentSuccessful").doesNotExist());
        mvc.perform(get("/orders/" + orders.findAll().getFirst().getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CREATED"));
        mvc.perform(get("/orders/999999")).andExpect(status().isNotFound());
    }

    @Test void rejectsInvalidAmountsQuantitiesProductsAndClientOwnedState() throws Exception {
        for (String body : new String[] {
            "{\"productName\":\"Laptop\",\"quantity\":-1,\"price\":2}",
            "{\"productName\":\"Laptop\",\"quantity\":0,\"price\":2}",
            "{\"productName\":\"Laptop\",\"quantity\":1.5,\"price\":2}",
            "{\"productName\":\"  \",\"quantity\":1,\"price\":2}",
            "{\"productName\":\"Laptop\",\"quantity\":1,\"price\":-2}",
            "{\"productName\":\"Laptop\",\"quantity\":1,\"price\":0.001}",
            "{\"productName\":\"Laptop\",\"quantity\":1}",
            "{\"id\":1,\"productName\":\"Laptop\",\"quantity\":1,\"price\":2}",
            "{\"status\":\"COMPLETED\",\"productName\":\"Laptop\",\"quantity\":1,\"price\":2}"
        }) {
            mvc.perform(post("/orders").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(orders.count()).isZero();
        assertThat(outbox.count()).isZero();
    }
}
