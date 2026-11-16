package com.ticketya.payment.repository;

import com.ticketya.payment.entity.Payment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    /**
     * Reintegro acumulado atómico: suma solo si el pago está APPROVED y el total reintegrado no supera lo pagado.
     * Nunca se lee y luego se escribe. 0 filas => el service diagnostica 404 / 409 / 422.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Payment p set p.refundedAmount = p.refundedAmount + :amount
             where p.id = :id and p.status = com.ticketya.payment.entity.PaymentStatus.APPROVED
               and p.refundedAmount + :amount <= p.amount""")
    int addRefunded(UUID id, BigDecimal amount);

    /** Compensación cuando la pasarela rechaza un reintegro ya contabilizado en la misma transacción. */
    @Modifying(flushAutomatically = true)
    @Query("update Payment p set p.refundedAmount = p.refundedAmount - :amount where p.id = :id and p.refundedAmount >= :amount")
    int subtractRefunded(UUID id, BigDecimal amount);
}
