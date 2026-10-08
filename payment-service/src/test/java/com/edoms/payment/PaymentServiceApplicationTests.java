package com.edoms.payment;

import com.edoms.payment.dto.*;
import com.edoms.payment.model.*;
import com.edoms.payment.repository.PaymentRepository;
import com.edoms.payment.outbox.*;
import com.edoms.payment.service.*;
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
class PaymentServiceApplicationTests {
    @Autowired PaymentService service;
    @Autowired PaymentRepository payments;
    @Autowired OutboxRepository outbox;
    @Autowired KafkaConsumerService consumer;
    @MockitoBean KafkaProducerService producer;

    @BeforeEach void resetData() { outbox.deleteAll(); payments.deleteAll(); }
    OrderCreatedEvent event(long id, String amount) {
        return OrderCreatedEvent.builder().orderId(id).productName("Laptop")
                .quantity(1).price(new BigDecimal(amount)).build();
    }
    Payment payment() { return payments.findAll().getFirst(); }

    @Test void duplicateDeliveryCreatesOnePaymentAndOneResult() {
        service.processPayment(event(1, "10.10"));
        service.processPayment(event(1, "10.10"));
        assertThat(payments.count()).isEqualTo(1);
        assertThat(payment().getAmount()).isEqualByComparingTo("10.10");
        assertThat(payment().getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(producer, times(1)).sendPaymentProcessedEvent(any());
    }

    @Test void enforcesPaymentLimitExactly() {
        service.processPayment(event(1, "10000.00"));
        service.processPayment(event(2, "10000.01"));
        assertThat(payments.findAll()).extracting(Payment::getPaymentStatus)
                .containsExactlyInAnyOrder(PaymentStatus.SUCCESS, PaymentStatus.FAILED);
    }

    @Test void simultaneousDuplicatesConvergeAfterRetry() {
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> service.processPayment(event(1, "10.00")))
                        .exceptionally(error -> null),
                CompletableFuture.runAsync(() -> service.processPayment(event(1, "10.00")))
                        .exceptionally(error -> null)).join();
        service.processPayment(event(1, "10.00"));
        assertThat(payments.count()).isEqualTo(1);
        verify(producer, times(1)).sendPaymentProcessedEvent(any());
    }

    @Test void refundsSuccessfulPaymentOnlyOnce() {
        service.processPayment(event(1, "12.00"));
        service.cancelPayment(event(1, "12.00"));
        service.cancelPayment(event(1, "12.00"));
        service.processPayment(event(1, "12.00"));
        assertThat(payments.count()).isEqualTo(1);
        assertThat(payment().getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(producer, times(1)).sendPaymentProcessedEvent(any());
    }

    @Test void earlyCancellationPreventsLateCharge() {
        service.cancelPayment(event(1, "12.00"));
        service.processPayment(event(1, "12.00"));
        assertThat(payment().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verifyNoInteractions(producer);
    }

    @Test void rejectedPaymentIsNotRefunded() {
        service.processPayment(event(1, "15000.00"));
        service.cancelPayment(event(1, "15000.00"));
        assertThat(payment().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test void resultEnqueueFailureRollsBackPaymentForRetry() {
        doThrow(new IllegalStateException("database unavailable")).when(producer).sendPaymentProcessedEvent(any());
        assertThatThrownBy(() -> service.processPayment(event(1, "20.00")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(payments.count()).isZero();
    }

    @Test void rejectsInvalidAndMalformedEvents() {
        assertThatThrownBy(() -> service.processPayment(event(1, "-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumer.consumeOrderEvent("{"))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> consumer.consumeOrderEvent("null"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(payments.count()).isZero();
    }

    @Test void listenerRoutesCancellation() throws Exception {
        consumer.consumeOrderEvent("{\"orderId\":1,\"productName\":\"Laptop\",\"quantity\":1,"
                + "\"price\":20,\"eventType\":\"ORDER_CANCELLED\"}");
        assertThat(payment().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
}
