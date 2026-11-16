package com.ticketya.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.order.exception.OrderException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseOrderTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    private PurchaseOrder order() {
        UUID sector = UUID.randomUUID();
        return PurchaseOrder.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(86400),
                "ARS", UUID.randomUUID(), NOW.plusSeconds(600), "k1", "h1",
                List.of(new OrderItem.Snapshot(sector, UUID.randomUUID(), "Platea A-1", new BigDecimal("1500.00"), "ARS"),
                        new OrderItem.Snapshot(sector, UUID.randomUUID(), "Platea A-2", new BigDecimal("1500.00"), "ARS")),
                NOW);
    }

    @Test
    void nace_pendiente_con_total_snapshot() {
        PurchaseOrder o = order();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(o.getTotal()).isEqualByComparingTo("3000.00");
        assertThat(o.quantity()).isEqualTo(2);
    }

    @Test
    void confirmar_limpia_la_reserva() {
        PurchaseOrder o = order();
        o.registrarPagoAprobado(UUID.randomUUID(), "CARD", "4242");
        o.confirm();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.reservaExpirada(NOW.plusSeconds(99999))).isFalse();
    }

    @Test
    void desde_un_estado_terminal_no_hay_mas_transiciones() {
        PurchaseOrder o = order();
        o.cancel();
        assertThatThrownBy(o::confirm).isInstanceOf(OrderException.class);
        assertThatThrownBy(o::expire).isInstanceOf(OrderException.class);
        assertThatThrownBy(o::rechazarPago).isInstanceOf(OrderException.class);
    }

    @Test
    void la_reserva_vence_en_el_instante_exacto() {
        PurchaseOrder o = order();
        assertThat(o.reservaExpirada(NOW.plusSeconds(599))).isFalse();
        assertThat(o.reservaExpirada(NOW.plusSeconds(600))).isTrue();
    }

    @Test
    void solo_el_dueno_es_dueno() {
        PurchaseOrder o = order();
        assertThat(o.esDuenio(o.getCustomerId())).isTrue();
        assertThat(o.esDuenio(UUID.randomUUID())).isFalse();
    }
}
