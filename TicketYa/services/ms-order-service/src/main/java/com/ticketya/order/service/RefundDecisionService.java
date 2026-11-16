package com.ticketya.order.service;

import com.ticketya.order.dto.response.RefundRequestResponse;
import com.ticketya.order.entity.OrderItem;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.entity.RefundRequest;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.mapper.RefundRequestMapper;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.order.repository.RefundRequestRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decisión del ADMIN. Aprobar solo deja la solicitud en PROCESSING: la saga la ejecuta el procesador
 * (job de recuperación), así la respuesta no depende de que payment, ticket e inventory estén disponibles.
 * La ventana de 72 h NO se revalida: la solicitud fue legítima al crearse.
 * Orden de bloqueo global: primero la orden, después la solicitud (evita deadlocks con el procesador).
 */
@Service
public class RefundDecisionService {

    private final OrderRepository orderRepo;
    private final RefundRequestRepository refundRepo;
    private final RefundRequestMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RefundDecisionService(OrderRepository orderRepo, RefundRequestRepository refundRepo,
                                 RefundRequestMapper mapper, TransactionTemplate tx, Clock clock) {
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
        this.mapper = mapper;
        this.tx = tx;
        this.clock = clock;
    }

    public RefundRequestResponse approve(Identity caller, UUID refundRequestId) {
        caller.requireAnyRole(Role.ADMIN);
        return decide(refundRequestId, (order, request) -> request.approve(caller.userId(), clock.instant()));
    }

    public RefundRequestResponse reject(Identity caller, UUID refundRequestId, String reason) {
        caller.requireAnyRole(Role.ADMIN);
        return decide(refundRequestId, (order, request) -> {
            request.reject(caller.userId(), reason, clock.instant());
            OrderItem item = order.findItem(request.getOrderItemId());
            if (item != null) {
                item.restoreActive();                       // el ítem vuelve a ACTIVE
            }
        });
    }

    private RefundRequestResponse decide(UUID id, java.util.function.BiConsumer<PurchaseOrder, RefundRequest> action) {
        UUID orderId = refundRepo.findOrderId(id)
                .orElseThrow(() -> OrderException.noEncontrado("Solicitud inexistente: " + id));
        return tx.execute(status -> {
            PurchaseOrder order = orderRepo.findByIdForUpdate(orderId)
                    .orElseThrow(() -> OrderException.noEncontrado("Orden inexistente: " + orderId));
            RefundRequest request = refundRepo.findByIdForUpdate(id)
                    .orElseThrow(() -> OrderException.noEncontrado("Solicitud inexistente: " + id));
            action.accept(order, request);
            return mapper.toResponse(request);
        });
    }
}
