package com.edoms.inventory.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_reservations")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Reservation {
    @Id
    private Long orderId;
    @Column(nullable = false)
    private String productName;
    private int quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;
}
