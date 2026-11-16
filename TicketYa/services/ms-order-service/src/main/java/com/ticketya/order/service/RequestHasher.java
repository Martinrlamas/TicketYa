package com.ticketya.order.service;

import com.ticketya.order.dto.request.CreateOrderRequest;
import com.ticketya.platform.idempotency.Fingerprint;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Huella canónica del pedido: no depende del orden de los ítems ni de los asientos. */
@Component
public class RequestHasher {

    public String hash(CreateOrderRequest req) {
        String items = req.items().stream()
                .sorted(Comparator.comparing(CreateOrderRequest.Item::sectorId))
                .map(i -> i.sectorId() + "|" + seats(i.seatIds()) + "|" + (i.quantity() == null ? "" : i.quantity()))
                .collect(Collectors.joining(";"));
        return Fingerprint.sha256(req.eventId() + "#" + items);
    }

    private static String seats(List<UUID> seatIds) {
        if (seatIds == null) {
            return "";
        }
        return seatIds.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    }
}
