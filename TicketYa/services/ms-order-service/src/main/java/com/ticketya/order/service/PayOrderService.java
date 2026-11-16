package com.ticketya.order.service;

import com.ticketya.order.client.InventoryPort;
import com.ticketya.order.client.InventoryPort.StockItem;
import com.ticketya.order.client.PaymentPort;
import com.ticketya.order.client.PaymentPort.ChargeCommand;
import com.ticketya.order.client.PaymentPort.PaymentInfo;
import com.ticketya.order.client.TicketPort;
import com.ticketya.order.client.TicketPort.TicketInfo;
import com.ticketya.order.client.TicketPort.TicketItem;
import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.dto.request.PayOrderRequest;
import com.ticketya.order.dto.response.OrderResponse;
import com.ticketya.order.entity.OrderItem;
import com.ticketya.order.entity.PayAttempt;
import com.ticketya.order.entity.PayAttemptResult;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.mapper.OrderMapper;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.order.repository.PayAttemptRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Paso 2 de la saga: pagar. Es un ORQUESTADOR sin transacción propia que alterna pasos de base cortos
 * ({@link PayOrderTransactions}) con llamadas a payment, inventory y ticket.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Roll-forward idempotente:</b> cada llamada remota es idempotente (clave {@code pay-{orderId}},
 *       confirm de hold, emisión por orderItemId, refund por clave, sold/release por releaseId). Por eso un
 *       reintento, una solicitud concurrente o el job de reconciliación pueden repetir cualquier paso.</li>
 *   <li><b>Estado persistido:</b> {@code pay_attempt.result} indica en qué paso quedó la saga.</li>
 *   <li><b>Compensación sin vuelta atrás:</b> al empezar a reintegrar se asienta COMPENSATING y solo se continúa
 *       reintegrando.</li>
 * </ul>
 */
@Service
public class PayOrderService {

    private static final Logger log = LoggerFactory.getLogger(PayOrderService.class);

    /** Respuestas de inventory al confirmar que significan "esa venta ya no es posible". */
    private static final Set<String> SALE_IMPOSSIBLE = Set.of("HOLD_EXPIRED", "INVALID_STATE_TRANSITION", "NOT_FOUND");

    private final PayOrderTransactions txOps;
    private final OrderRepository orderRepo;
    private final PayAttemptRepository attemptRepo;
    private final PaymentPort payments;
    private final InventoryPort inventory;
    private final TicketPort tickets;
    private final HoldReleaser holdReleaser;
    private final RetryExecutor retry;
    private final OrderMapper mapper;
    private final OrderProperties props;
    private final Clock clock;

    public PayOrderService(PayOrderTransactions txOps, OrderRepository orderRepo, PayAttemptRepository attemptRepo,
                           PaymentPort payments, InventoryPort inventory, TicketPort tickets,
                           HoldReleaser holdReleaser, RetryExecutor retry, OrderMapper mapper,
                           OrderProperties props, Clock clock) {
        this.txOps = txOps;
        this.orderRepo = orderRepo;
        this.attemptRepo = attemptRepo;
        this.payments = payments;
        this.inventory = inventory;
        this.tickets = tickets;
        this.holdReleaser = holdReleaser;
        this.retry = retry;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ pagar

    public OrderResponse pay(Identity caller, UUID orderId, String idempotencyKey, PayOrderRequest req) {
        caller.requireAnyRole(Role.CUSTOMER);

        PayOrderTransactions.Start start = txOps.begin(orderId, caller.userId(), IdempotencyKey.require(idempotencyKey));
        switch (start.kind()) {
            case REPLAY_CONFIRMED:
                return confirmedView(orderId);                                   // mismo resultado, sin cobrar de nuevo
            case REPLAY_REJECTED:
                throw OrderException.pagoRechazado();
            case REPLAY_COMPENSATED:
                throw compensationError(start.reason());
            case EVENT_CANCELLED:
                holdReleaser.releaseQuietly(start.holdId());
                throw OrderException.eventoNoComprable("El evento fue cancelado");
            case EXPIRED_NOW:
                holdReleaser.releaseQuietly(start.holdId());
                throw OrderException.reservaVencida("La reserva venció antes de pagar");
            default:
                break;
        }

        PurchaseOrder order = load(orderId);
        // Clave determinística por orden: payment-service cobra UNA vez aunque este paso se repita
        PaymentInfo payment = payments.charge(new ChargeCommand(orderId, order.getCustomerId(), order.getTotal(),
                order.getCurrency(), req.method().name(), req.token(), req.last4(), paymentKey(orderId)));
        return afterCharge(orderId, payment);
    }

    private OrderResponse afterCharge(UUID orderId, PaymentInfo payment) {
        if (payment.isRejected()) {
            UUID holdId = txOps.recordRejected(orderId);
            holdReleaser.releaseQuietly(holdId);
            throw OrderException.pagoRechazado();
        }
        if (!payment.isApproved()) {
            throw OrderException.servicioNoDisponible("Estado de pago desconocido: " + payment.status());
        }
        txOps.recordApproved(orderId, payment.paymentId(), payment.method(), payment.last4());
        return continueAfterApproval(orderId);
    }

    /** Pago aprobado: confirmar hold -> emitir entradas -> CONFIRMED. Compensa si la venta es imposible. */
    private OrderResponse continueAfterApproval(UUID orderId) {
        PurchaseOrder order = load(orderId);

        try {
            retry.run("confirmar hold " + order.getHoldId(), () -> inventory.confirmHold(order.getHoldId()));
        } catch (OrderException e) {
            if (SALE_IMPOSSIBLE.contains(e.getCode())) {
                compensate(orderId, PayAttempt.REASON_HOLD_EXPIRED);
                throw OrderException.reservaVencida("La reserva venció durante el pago; el pago fue reintegrado");
            }
            throw e;     // inventory caído: el intento queda APPROVED y la reconciliación reanuda desde acá
        }

        List<TicketInfo> issued;
        try {
            issued = retry.run("emitir entradas de la orden " + orderId, () -> issueTickets(order));
        } catch (OrderException e) {
            log.error("no se pudieron emitir las entradas de la orden {}: {}", orderId, e.getMessage());
            compensate(orderId, PayAttempt.REASON_TICKETS_FAILED);
            throw OrderException.servicioNoDisponible("No se pudieron emitir las entradas; el pago fue reintegrado");
        }

        txOps.completeConfirmed(orderId);
        return mapper.toResponse(load(orderId), issued);
    }

    // ------------------------------------------------------------ compensación

    private void compensate(UUID orderId, String reason) {
        txOps.iniciarCompensacion(orderId, reason);
        ejecutarCompensacion(orderId);
    }

    /** Reintegro total + (si ya se había vendido) devolución de lugares + FAILED_REFUNDED. Todo idempotente. */
    private void ejecutarCompensacion(UUID orderId) {
        PurchaseOrder order = load(orderId);
        String reason = attemptRepo.findByOrderIdAndResultIn(orderId, EnumSet.of(PayAttemptResult.COMPENSATING))
                .stream().findFirst().map(PayAttempt::getReason).orElse(PayAttempt.REASON_HOLD_EXPIRED);

        payments.refund(order.getPaymentId(), order.getTotal(), "ORDER_COMPENSATION", "refund-" + orderId);
        if (PayAttempt.REASON_TICKETS_FAILED.equals(reason)) {
            inventory.releaseSold(order.getEventId(), releaseIdFor(orderId), stockItemsOf(order));
        }
        txOps.finalizarCompensacion(orderId);
        log.warn("order compensated orderId={} reason={}", orderId, reason);
    }

    // ------------------------------------------------------------ reconciliación

    /**
     * Reanuda una saga que quedó a medias (timeout del cobro, inventory o payment caídos, reinicio del servicio).
     * Los resultados "de negocio" (402, 410, 503) son esperados y no se propagan: solo se registran.
     */
    public void resume(UUID orderId) {
        try {
            List<PayAttempt> inFlight = attemptRepo.findByOrderIdAndResultIn(orderId, PayAttemptResult.IN_FLIGHT);
            if (inFlight.isEmpty()) {
                return;
            }
            PayAttempt attempt = inFlight.get(0);
            switch (attempt.getResult()) {
                case PENDING -> reanudarPendientes(orderId, attempt);
                case APPROVED -> continueAfterApproval(orderId);
                case COMPENSATING -> ejecutarCompensacion(orderId);
                default -> { }
            }
        } catch (OrderException e) {
            log.info("reconciliación de la orden {}: {} ({})", orderId, e.getCode(), e.getMessage());
        }
    }

    private void reanudarPendientes(UUID orderId, PayAttempt attempt) {
        List<PaymentInfo> found = payments.findByOrder(orderId);
        Optional<PaymentInfo> decisive = found.stream().filter(PaymentInfo::isApproved).findFirst()
                .or(() -> found.stream().filter(PaymentInfo::isRejected).findFirst());
        if (decisive.isPresent()) {
            afterCharge(orderId, decisive.get());
            return;
        }
        Duration age = Duration.between(attempt.getCreatedAt(), clock.instant());
        if (age.compareTo(props.jobs().reconcile().abandonAfter()) > 0) {
            txOps.abandon(orderId);
            log.warn("pago de la orden {} sin rastro en payment-service tras {}: intento abandonado", orderId, age);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Respuesta para un reintento de un pago ya confirmado: las mismas entradas (la emisión es idempotente). */
    private OrderResponse confirmedView(UUID orderId) {
        PurchaseOrder order = load(orderId);
        return mapper.toResponse(order, issueTickets(order));
    }

    private List<TicketInfo> issueTickets(PurchaseOrder order) {
        List<TicketItem> items = order.getItems().stream()
                .map(i -> new TicketItem(i.getId(), i.getSectorId(), i.getSeatId(), i.getSeatLabel()))
                .toList();
        return tickets.issue(order.getId(), order.getEventId(), order.getCustomerId(), items);
    }

    private static List<StockItem> stockItemsOf(PurchaseOrder order) {
        Map<UUID, List<UUID>> seatsBySector = new LinkedHashMap<>();
        Map<UUID, Integer> zoneQuantity = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            if (item.getSeatId() != null) {
                seatsBySector.computeIfAbsent(item.getSectorId(), k -> new ArrayList<>()).add(item.getSeatId());
            } else {
                zoneQuantity.merge(item.getSectorId(), 1, Integer::sum);
            }
        }
        List<StockItem> result = new ArrayList<>();
        seatsBySector.forEach((sector, seats) -> result.add(StockItem.seats(sector, seats)));
        zoneQuantity.forEach((sector, qty) -> result.add(StockItem.zone(sector, qty)));
        result.sort(Comparator.comparing(StockItem::sectorId));
        return result;
    }

    private PurchaseOrder load(UUID orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> OrderException.noEncontrado("Orden inexistente: " + orderId));
    }

    private static OrderException compensationError(String reason) {
        return PayAttempt.REASON_TICKETS_FAILED.equals(reason)
                ? OrderException.servicioNoDisponible("No se pudieron emitir las entradas; el pago fue reintegrado")
                : OrderException.reservaVencida("La reserva venció durante el pago; el pago fue reintegrado");
    }

    static String paymentKey(UUID orderId) {
        return "pay-" + orderId;
    }

    static UUID releaseIdFor(UUID orderId) {
        return UUID.nameUUIDFromBytes(("order-release:" + orderId).getBytes(StandardCharsets.UTF_8));
    }

}
