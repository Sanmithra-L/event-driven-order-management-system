package com.edoms.inventory;

import com.edoms.inventory.dto.*;
import com.edoms.inventory.model.*;
import com.edoms.inventory.repository.*;
import com.edoms.inventory.outbox.OutboxRepository;
import com.edoms.inventory.service.*;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceApplicationTests {
    @Autowired InventoryService service;
    @Autowired InventoryRepository inventory;
    @Autowired ReservationRepository reservations;
    @Autowired OutboxRepository outbox;
    @Autowired KafkaConsumerService consumer;
    @MockitoBean KafkaProducerService producer;

    @BeforeEach void resetData() {
        outbox.deleteAll(); reservations.deleteAll(); inventory.deleteAll();
        inventory.save(Inventory.builder().productName("Laptop").availableStock(5).build());
    }
    OrderCreatedEvent event(long id, int quantity) {
        return OrderCreatedEvent.builder().orderId(id).productName("Laptop")
                .quantity(quantity).price(new BigDecimal("20.00")).build();
    }
    Inventory stock() { return inventory.findAll().getFirst(); }

    @Test void duplicateDeliveryReservesOnce() {
        service.reserveInventory(event(1, 2));
        service.reserveInventory(event(1, 2));
        assertThat(stock().getAvailableStock()).isEqualTo(3);
        assertThat(stock().getReservedStock()).isEqualTo(2);
        assertThat(reservations.count()).isEqualTo(1);
        verify(producer, times(1)).sendInventoryUpdatedEvent(any());
    }

    @Test void insufficientOrUnknownProductDoesNotChangeStock() {
        service.reserveInventory(event(1, 6));
        OrderCreatedEvent missing = event(2, 1); missing.setProductName("Unknown");
        service.reserveInventory(missing);
        service.releaseInventory(event(1, 6));
        assertThat(stock().getAvailableStock()).isEqualTo(5);
        assertThat(stock().getReservedStock()).isZero();
        assertThat(reservations.findAll()).allMatch(r -> r.getStatus() == ReservationStatus.INSUFFICIENT);
    }

    @Test void duplicateReleaseUsesActualReservationQuantity() {
        service.reserveInventory(event(1, 2));
        service.releaseInventory(event(1, 100));
        service.releaseInventory(event(1, 100));
        service.reserveInventory(event(1, 2));
        assertThat(stock().getAvailableStock()).isEqualTo(5);
        assertThat(stock().getReservedStock()).isZero();
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test void earlyCancellationDoesNotCreateStockOrReserveLater() {
        service.releaseInventory(event(1, 2));
        service.reserveInventory(event(1, 2));
        assertThat(stock().getAvailableStock()).isEqualTo(5);
        assertThat(stock().getReservedStock()).isZero();
        verifyNoInteractions(producer);
    }

    @Test void concurrentOrdersCannotOversell() {
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> service.reserveInventory(event(1, 4))),
                CompletableFuture.runAsync(() -> service.reserveInventory(event(2, 4)))).join();
        assertThat(stock().getAvailableStock()).isEqualTo(1);
        assertThat(stock().getReservedStock()).isEqualTo(4);
        assertThat(reservations.findAll()).extracting(Reservation::getStatus)
                .containsExactlyInAnyOrder(ReservationStatus.RESERVED, ReservationStatus.INSUFFICIENT);
    }

    @Test void simultaneousDuplicatesConvergeAfterRetry() {
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> service.reserveInventory(event(1, 2)))
                        .exceptionally(error -> null),
                CompletableFuture.runAsync(() -> service.reserveInventory(event(1, 2)))
                        .exceptionally(error -> null)).join();
        service.reserveInventory(event(1, 2));
        assertThat(reservations.count()).isEqualTo(1);
        assertThat(stock().getAvailableStock()).isEqualTo(3);
        assertThat(stock().getReservedStock()).isEqualTo(2);
        verify(producer, times(1)).sendInventoryUpdatedEvent(any());
    }

    @Test void resultEnqueueFailureRollsBackReservationAndStock() {
        doThrow(new IllegalStateException("database unavailable")).when(producer).sendInventoryUpdatedEvent(any());
        assertThatThrownBy(() -> service.reserveInventory(event(1, 2))).isInstanceOf(IllegalStateException.class);
        assertThat(stock().getAvailableStock()).isEqualTo(5);
        assertThat(reservations.count()).isZero();
    }

    @Test void rejectsInvalidQuantitiesAndMalformedMessages() {
        assertThatThrownBy(() -> service.reserveInventory(event(1, -1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumer.consumeOrderEvent("{"))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThat(stock().getAvailableStock()).isEqualTo(5);
        assertThat(reservations.count()).isZero();
    }

    @Test void listenerRoutesCancellation() throws Exception {
        service.reserveInventory(event(1, 2));
        consumer.consumeOrderEvent("{\"orderId\":1,\"productName\":\"Laptop\",\"quantity\":2,"
                + "\"price\":20,\"eventType\":\"ORDER_CANCELLED\"}");
        assertThat(stock().getAvailableStock()).isEqualTo(5);
    }
}
