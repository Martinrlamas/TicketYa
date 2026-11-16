package com.ticketya.order.service;

import com.ticketya.order.entity.PayAttemptResult;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.mapper.OrderMapper;
import com.ticketya.order.dto.response.OrderResponse;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.order.repository.PayAttemptRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** DELETE /api/orders/{id}: el dueño cancela una orden PENDING_PAYMENT; se libera cuota y hold. */
@Service
public class CancelOrderService {

    private final OrderRepository orderRepo;
    private final PayAttemptRepository attemptRepo;
    private final QuotaService quota;
    private final HoldReleaser holdReleaser;
    private final OrderMapper mapper;
    private final TransactionTemplate tx;

    public CancelOrderService(OrderRepository orderRepo, PayAttemptRepository attemptRepo, QuotaService quota,
                              HoldReleaser holdReleaser, OrderMapper mapper, TransactionTemplate tx) {
        this.orderRepo = orderRepo;
        this.attemptRepo = attemptRepo;
        this.quota = quota;
        this.holdReleaser = holdReleaser;
        this.mapper = mapper;
        this.tx = tx;
    }

    public OrderResponse cancel(Identity caller, UUID orderId) {
        caller.requireAnyRole(Role.CUSTOMER);
        UUID holdId = tx.execute(status -> {
            PurchaseOrder order = orderRepo.findByIdForUpdate(orderId)
                    .filter(o -> o.esDuenio(caller.userId()))
                    .orElseThrow(() -> OrderException.noEncontrado("Orden inexistente: " + orderId));
            if (!order.isPending()) {
                throw OrderException.estadoInvalido("La orden está " + order.getStatus());
            }
            if (!attemptRepo.findByOrderIdAndResultIn(orderId, PayAttemptResult.IN_FLIGHT).isEmpty()) {
                throw OrderException.estadoInvalido("Hay un pago en curso para esta orden");
            }
            order.cancel();
            quota.release(order);
            return order.getHoldId();
        });
        holdReleaser.releaseQuietly(holdId);              // fuera de la transacción: best effort
        return mapper.toResponse(orderRepo.findById(orderId).orElseThrow());
    }
}
