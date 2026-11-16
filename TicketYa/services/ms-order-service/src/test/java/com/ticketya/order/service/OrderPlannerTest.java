package com.ticketya.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.order.client.CatalogPort.SaleInfo;
import com.ticketya.order.client.CatalogPort.SectorSale;
import com.ticketya.order.dto.request.CreateOrderRequest;
import com.ticketya.order.exception.OrderException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPlannerTest {

    private final OrderPlanner planner = new OrderPlanner();
    private final UUID eventId = UUID.randomUUID();
    private final UUID platea = UUID.randomUUID();
    private final UUID campo = UUID.randomUUID();
    private final UUID seat1 = UUID.randomUUID();
    private final UUID seat2 = UUID.randomUUID();

    private final SaleInfo info = new SaleInfo(eventId, "PUBLISHED", true, Instant.now().plusSeconds(3600), List.of(
            new SectorSale(platea, "Platea", "NUMBERED", new BigDecimal("2000.00"), "ARS"),
            new SectorSale(campo, "Campo", "GENERAL", new BigDecimal("800.00"), "ARS")));

    private final Map<UUID, String> labels = Map.of(seat1, "A-1", seat2, "A-2");

    @Test
    void asientos_numerados_generan_un_item_por_asiento_con_etiqueta() {
        var req = new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(platea, List.of(seat1, seat2), null)));
        OrderPlan plan = planner.plan(req, info);
        assertThat(plan.quantity()).isEqualTo(2);
        assertThat(plan.snapshots(labels)).extracting("seatLabel").containsExactly("Platea A-1", "Platea A-2");
        assertThat(plan.snapshots(labels)).allMatch(s -> s.unitPrice().compareTo(new BigDecimal("2000.00")) == 0);
    }

    @Test
    void zona_general_genera_un_item_por_lugar() {
        var req = new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(campo, null, 3)));
        OrderPlan plan = planner.plan(req, info);
        assertThat(plan.quantity()).isEqualTo(3);
        assertThat(plan.snapshots(Map.of())).allMatch(s -> s.seatId() == null && "Campo".equals(s.seatLabel()));
    }

    @Test
    void sector_ajeno_al_evento_es_invalido() {
        var req = new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(UUID.randomUUID(), null, 1)));
        assertThatThrownBy(() -> planner.plan(req, info)).isInstanceOf(OrderException.class);
    }

    @Test
    void numerado_con_quantity_es_invalido() {
        var req = new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(platea, null, 2)));
        assertThatThrownBy(() -> planner.plan(req, info)).isInstanceOf(OrderException.class);
    }

    @Test
    void sector_repetido_es_invalido() {
        var req = new CreateOrderRequest(eventId, List.of(
                new CreateOrderRequest.Item(campo, null, 1), new CreateOrderRequest.Item(campo, null, 1)));
        assertThatThrownBy(() -> planner.plan(req, info)).isInstanceOf(OrderException.class);
    }
}
