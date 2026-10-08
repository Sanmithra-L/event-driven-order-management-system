package com.edoms.order.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity class representing an Order in the order management system.
 * Created as part of the event-driven microservice architecture.
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String productName;
    
    private int quantity;
    
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @JsonIgnore
    private Boolean paymentSuccessful;

    @JsonIgnore
    private Boolean inventoryAvailable;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
}
