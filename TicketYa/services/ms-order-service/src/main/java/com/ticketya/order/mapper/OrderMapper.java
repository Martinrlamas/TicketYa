package com.ticketya.order.mapper;

import com.ticketya.order.client.TicketPort;
import com.ticketya.order.dto.response.OrderItemResponse;
import com.ticketya.order.dto.response.OrderResponse;
import com.ticketya.order.dto.response.PaymentSummary;
import com.ticketya.order.dto.response.TicketDto;
import com.ticketya.order.entity.OrderStatus;
import com.ticketya.order.entity.PurchaseOrder;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(PurchaseOrder o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getSectorId(), i.getSeatId(), i.getSeatLabel(),
                        i.getUnitPrice(), i.getCurrency(), i.getStatus()))
                .toList();
        return new OrderResponse(o.getId(), o.getCustomerId(), o.getEventId(), o.getEventStartsAt(), o.getStatus(),
                items, o.getTotal(), o.getCurrency(), o.getHoldExpiresAt(), o.getCreatedAt(),
                o.getPaymentId(), paymentSummary(o), null);
    }

    public OrderResponse toResponse(PurchaseOrder o, List<TicketPort.TicketInfo> tickets) {
        return toResponse(o).withTickets(tickets.stream().map(this::toDto).toList());
    }

    public TicketDto toDto(TicketPort.TicketInfo t) {
        return new TicketDto(t.ticketId(), t.orderItemId(), t.eventId(), t.sectorId(), t.seatId(),
                t.seatLabel(), t.qrCode(), t.status(), t.issuedAt());
    }

    /** El estado del pago se deduce del de la orden: solo hay pago registrado si se aprobó. */
    private PaymentSummary paymentSummary(PurchaseOrder o) {
        if (o.getPaymentId() == null) {
            return null;
        }
        String status = o.getStatus() == OrderStatus.FAILED_REFUNDED ? "REFUNDED" : "APPROVED";
        return new PaymentSummary(o.getPaymentMethod(), o.getPaymentLast4(), status);
    }
}
