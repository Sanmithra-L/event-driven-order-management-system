package com.edoms.payment.repository;

import com.edoms.payment.model.Payment;
import com.edoms.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for Payment entity.
 * Provides CRUD operations and custom query methods for payment management.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    /**
     * Find payment by order ID.
     * 
     * @param orderId the order ID
     * @return Optional containing the payment if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByOrderId(Long orderId);
    
    /**
     * Find all payments by status.
     * 
     * @param status the payment status
     * @return list of payments with the specified status
     */
    List<Payment> findByPaymentStatus(PaymentStatus status);
}
