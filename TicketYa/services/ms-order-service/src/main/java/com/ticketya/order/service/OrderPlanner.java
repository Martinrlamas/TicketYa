package com.ticketya.order.service;

import com.ticketya.order.client.CatalogPort.SaleInfo;
import com.ticketya.order.client.CatalogPort.SectorSale;
import com.ticketya.order.client.InventoryPort.StockItem;
import com.ticketya.order.dto.request.CreateOrderRequest;
import com.ticketya.order.service.OrderPlan.PlannedItem;
import com.ticketya.order.exception.OrderException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lógica pura (sin I/O): valida el pedido contra lo que informa catalog y calcula los ítems con el precio
 * vigente (snapshot). En zonas generales genera UN ítem por lugar. Que los asientos existan y estén libres
 * lo decide inventory al crear el hold.
 */
@Component
public class OrderPlanner {

    public OrderPlan plan(CreateOrderRequest req, SaleInfo info) {
        List<PlannedItem> planned = new ArrayList<>();
        List<StockItem> stockItems = new ArrayList<>();
        Set<UUID> seenSectors = new HashSet<>();
        String currency = null;

        for (CreateOrderRequest.Item item : req.items()) {
            if (!seenSectors.add(item.sectorId())) {
                throw OrderException.validacion("El sector " + item.sectorId() + " aparece más de una vez");
            }
            SectorSale sale = info.sector(item.sectorId());
            if (sale == null) {
                throw OrderException.validacion("El sector " + item.sectorId() + " no pertenece al evento");
            }
            if (item.isNumbered() != sale.isNumbered()) {
                throw OrderException.validacion(sale.isNumbered()
                        ? "El sector " + item.sectorId() + " es numerado: usar seatIds"
                        : "El sector " + item.sectorId() + " es zona general: usar quantity");
            }
            if (currency == null) {
                currency = sale.currency();
            } else if (!currency.equals(sale.currency())) {
                throw OrderException.validacion("No se pueden mezclar monedas en una misma orden");
            }

            if (sale.isNumbered()) {
                Set<UUID> seatIds = new LinkedHashSet<>(item.seatIds());
                for (UUID seatId : seatIds) {
                    planned.add(new PlannedItem(item.sectorId(), seatId, sale.name(), sale.price(), sale.currency()));
                }
                stockItems.add(StockItem.seats(item.sectorId(), List.copyOf(seatIds)));
            } else {
                for (int n = 0; n < item.quantity(); n++) {
                    planned.add(new PlannedItem(item.sectorId(), null, sale.name(), sale.price(), sale.currency()));
                }
                stockItems.add(StockItem.zone(item.sectorId(), item.quantity()));
            }
        }
        return new OrderPlan(List.copyOf(planned), currency, List.copyOf(stockItems));
    }
}
