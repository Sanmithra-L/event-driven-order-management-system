package com.edoms.inventory.service;

import com.edoms.inventory.dto.*;
import com.edoms.inventory.model.*;
import com.edoms.inventory.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final KafkaProducerService kafkaProducerService;
    private final EntityManager entityManager;

    @Transactional
    public void reserveInventory(OrderCreatedEvent event) {
        OrderEventValidation.validate(event);
        if (reservationRepository.findLockedByOrderId(event.getOrderId()).isPresent()) return;
        Reservation reservation = Reservation.builder().orderId(event.getOrderId())
                .productName(event.getProductName()).quantity(event.getQuantity())
                .status(ReservationStatus.INSUFFICIENT).build();
        // Persist, rather than merge an assigned ID, so simultaneous duplicates
        // cannot overwrite an existing reservation after stock is deducted.
        entityManager.persist(reservation);
        entityManager.flush();
        inventoryRepository.findByProductName(event.getProductName()).ifPresent(inventory -> {
            if (inventory.getAvailableStock() >= event.getQuantity()) {
                inventory.setAvailableStock(inventory.getAvailableStock() - event.getQuantity());
                inventory.setReservedStock(inventory.getReservedStock() + event.getQuantity());
                reservation.setStatus(ReservationStatus.RESERVED);
                inventoryRepository.save(inventory);
            }
        });
        kafkaProducerService.sendInventoryUpdatedEvent(InventoryUpdatedEvent.builder()
                .orderId(event.getOrderId()).productName(event.getProductName()).quantity(event.getQuantity())
                .inventoryAvailable(reservation.getStatus() == ReservationStatus.RESERVED)
                .inventoryStatus(reservation.getStatus().name()).build());
    }

    @Transactional
    public void releaseInventory(OrderCreatedEvent event) {
        OrderEventValidation.validate(event);
        Reservation reservation = reservationRepository.findLockedByOrderId(event.getOrderId()).orElse(null);
        if (reservation == null) {
            // Retain a cancellation even if it arrives before creation.
            entityManager.persist(Reservation.builder().orderId(event.getOrderId())
                    .productName(event.getProductName()).quantity(event.getQuantity())
                    .status(ReservationStatus.RELEASED).build());
            return;
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) return;
        Inventory inventory = inventoryRepository.findByProductName(reservation.getProductName())
                .orElseThrow(() -> new IllegalStateException("Reserved product is missing"));
        if (inventory.getReservedStock() < reservation.getQuantity()) {
            throw new IllegalStateException("Reserved stock is inconsistent");
        }
        inventory.setAvailableStock(inventory.getAvailableStock() + reservation.getQuantity());
        inventory.setReservedStock(inventory.getReservedStock() - reservation.getQuantity());
        reservation.setStatus(ReservationStatus.RELEASED);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void initializeInventory() {
        if (inventoryRepository.count() == 0) {
            inventoryRepository.save(Inventory.builder().productName("Laptop").availableStock(50).build());
            inventoryRepository.save(Inventory.builder().productName("Mouse").availableStock(100).build());
            inventoryRepository.save(Inventory.builder().productName("Keyboard").availableStock(75).build());
        }
    }
}
