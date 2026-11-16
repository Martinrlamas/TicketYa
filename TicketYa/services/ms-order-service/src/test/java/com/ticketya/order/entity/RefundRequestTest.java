package com.ticketya.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.order.exception.OrderException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private final UUID admin = UUID.randomUUID();

    private RefundRequest pending() {
        return RefundRequest.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "no puedo ir", NOW);
    }

    @Test
    void nace_PENDING_y_al_aprobar_pasa_a_PROCESSING_lista_para_procesar() {
        RefundRequest r = pending();
        assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.PENDING);
        r.approve(admin, NOW);
        assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
        assertThat(r.getDecidedBy()).isEqualTo(admin);
        assertThat(r.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void una_solicitud_ya_decidida_no_se_puede_aprobar_ni_rechazar() {
        RefundRequest r = pending();
        r.reject(admin, "fuera de política", NOW);
        assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.REJECTED);
        assertThatThrownBy(() -> r.approve(admin, NOW)).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> r.reject(admin, null, NOW)).isInstanceOf(OrderException.class);
    }

    @Test
    void el_paso_alcanzado_nunca_retrocede() {
        RefundRequest r = pending();
        r.approve(admin, NOW);
        r.advanceTo(RefundStep.SEAT_RELEASED, NOW);
        r.advanceTo(RefundStep.TICKET_INVALIDATED, NOW);          // un procesador lento no pisa a uno adelantado
        assertThat(r.getStep()).isEqualTo(RefundStep.SEAT_RELEASED);
    }

    @Test
    void las_fallas_transitorias_se_reintentan_y_al_agotarse_pasa_a_FAILED() {
        RefundRequest r = pending();
        r.approve(admin, NOW);
        for (int i = 1; i < 3; i++) {
            r.registrarFalloTransitorio("payment caído", 3, NOW.plusSeconds(5), NOW);
            assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
            assertThat(r.getAttempts()).isEqualTo(i);
        }
        r.registrarFalloTransitorio("payment caído", 3, NOW.plusSeconds(5), NOW);
        assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.FAILED);
        assertThat(r.getNextAttemptAt()).isNull();
        assertThat(r.getLastError()).contains("payment caído");
    }

    @Test
    void una_falla_de_negocio_pasa_directo_a_FAILED() {
        RefundRequest r = pending();
        r.approve(admin, NOW);
        r.fallarDefinitivamente("la entrada ya fue usada", NOW);
        assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.FAILED);
    }

    @Test
    void la_cancelacion_del_evento_absorbe_la_solicitud_abierta_conservando_el_paso() {
        RefundRequest r = pending();
        r.approve(admin, NOW);
        r.advanceTo(RefundStep.TICKET_INVALIDATED, NOW);
        r.absorberPorCancelacionEvento(NOW.plusSeconds(1));
        assertThat(r.isAutomatic()).isTrue();
        assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
        assertThat(r.getStep()).isEqualTo(RefundStep.TICKET_INVALIDATED);
    }

    @Test
    void completar_deja_el_paso_final_y_una_completada_no_se_reabre() {
        RefundRequest r = pending();
        r.approve(admin, NOW);
        r.complete(NOW);
        assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
        assertThat(r.getStep()).isEqualTo(RefundStep.PAYMENT_REFUNDED);
        assertThatThrownBy(() -> r.absorberPorCancelacionEvento(NOW)).isInstanceOf(OrderException.class);
    }

    @Test
    void la_automatica_nace_PROCESSING() {
        RefundRequest r = RefundRequest.automatic(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "x", NOW);
        assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
        assertThat(r.isAutomatic()).isTrue();
    }
}
