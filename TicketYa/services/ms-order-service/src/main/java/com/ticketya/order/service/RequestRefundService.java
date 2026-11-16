package com.ticketya.order.service;

import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.dto.request.CreateRefundRequest;
import com.ticketya.order.dto.response.RefundRequestResponse;
import com.ticketya.order.entity.OrderItem;
import com.ticketya.order.entity.OrderItemStatus;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.entity.RefundRequest;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.mapper.RefundRequestMapper;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.order.repository.RefundRequestRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** El cliente pide devolver UNA entrada de una orden confirmada propia, hasta 72 h antes del evento. */
@Service
public class RequestRefundService {

    private final OrderRepository orderRepo;
    private final RefundRequestRepository refundRepo;
    private final RefundRequestMapper mapper;
    private final OrderProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RequestRefundService(OrderRepository orderRepo, RefundRequestRepository refundRepo,
                                RefundRequestMapper mapper, OrderProperties props, TransactionTemplate tx, Clock clock) {
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
        this.mapper = mapper;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    public RefundRequestResponse request(Identity caller, UUID orderId, UUID itemId, CreateRefundRequest req) {
        caller.requireAnyRole(Role.CUSTOMER);
        try {
            return tx.execute(status -> requestInTransaction(caller, orderId, itemId, req));
        } catch (DataIntegrityViolationException e) {
            // Respaldo: el índice único parcial impidió una segunda solicitud activa para el ítem
            throw OrderException.estadoInvalido("Ya existe una solicitud activa para el ítem");
        }
    }

    private RefundRequestResponse requestInTransaction(Identity caller, UUID orderId, UUID itemId, CreateRefundRequest req) {
        // Se bloquea la orden: dos solicitudes simultáneas sobre el mismo ítem se serializan
        PurchaseOrder order = orderRepo.findByIdForUpdate(orderId)
                .filter(o -> o.esDuenio(caller.userId()))
                .orElseThrow(() -> OrderException.noEncontrado("Orden inexistente: " + orderId));
        OrderItem item = order.findItem(itemId);
        if (item == null) {
            throw OrderException.noEncontrado("Ítem inexistente: " + itemId);
        }
        if (!order.isConfirmed()) {
            throw OrderException.estadoInvalido("La orden está " + order.getStatus());
        }
        if (item.getStatus() != OrderItemStatus.ACTIVE) {
            throw OrderException.estadoInvalido("Ya existe una solicitud activa o el ítem está " + item.getStatus());
        }
        Instant now = clock.instant();
        int hours = props.refunds().windowHours();
        // "Hasta 72 h antes": justo en el límite todavía se acepta
        if (now.isAfter(order.getEventStartsAt().minus(Duration.ofHours(hours)))) {
            throw OrderException.ventanaDevolucionCerrada(hours);
        }
        item.solicitarReintegro();
        RefundRequest request = refundRepo.saveAndFlush(RefundRequest.pending(order.getId(), item.getId(),
                order.getCustomerId(), order.getEventId(), req == null ? null : req.reason(), now));
        return mapper.toResponse(request);
    }
}
