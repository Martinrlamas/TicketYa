package com.ticketya.order.service;

import com.ticketya.order.client.TicketPort;
import com.ticketya.order.client.TicketPort.TicketItem;
import com.ticketya.order.dto.response.OrderResponse;
import com.ticketya.platform.web.PageResponse;
import com.ticketya.order.entity.OrderStatus;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.mapper.OrderMapper;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Consultas. Una orden ajena responde 404 (no se revela que existe). */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepo;
    private final TicketPort tickets;
    private final OrderMapper mapper;

    public OrderQueryService(OrderRepository orderRepo, TicketPort tickets, OrderMapper mapper) {
        this.orderRepo = orderRepo;
        this.tickets = tickets;
        this.mapper = mapper;
    }

    public PageResponse<OrderResponse> listarMios(Identity caller, OrderStatus status, int page, int size) {
        caller.requireAnyRole(Role.CUSTOMER);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = orderRepo.findAll((root, q, cb) -> status == null
                ? cb.equal(root.get("customerId"), caller.userId())
                : cb.and(cb.equal(root.get("customerId"), caller.userId()), cb.equal(root.get("status"), status)),
                pageable);
        return PageResponse.of(result, mapper::toResponse);
    }

    public OrderResponse obtenerUno(Identity caller, UUID orderId) {
        caller.requireAnyRole(Role.CUSTOMER, Role.ADMIN);
        PurchaseOrder order = orderRepo.findById(orderId)
                .filter(o -> caller.is(Role.ADMIN) || o.esDuenio(caller.userId()))
                .orElseThrow(() -> OrderException.noEncontrado("Orden inexistente: " + orderId));
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            return mapper.toResponse(order);
        }
        // La emisión es idempotente por orderItemId: sirve para consultar las entradas ya emitidas
        var items = order.getItems().stream()
                .map(i -> new TicketItem(i.getId(), i.getSectorId(), i.getSeatId(), i.getSeatLabel())).toList();
        return mapper.toResponse(order, tickets.issue(order.getId(), order.getEventId(), order.getCustomerId(), items));
    }
}
