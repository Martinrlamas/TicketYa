package com.ticketya.order.service;

import com.ticketya.platform.idempotency.IdempotencyLock;
import com.ticketya.order.client.CatalogPort;
import com.ticketya.order.client.InventoryPort;
import com.ticketya.order.client.InventoryPort.HoldInfo;
import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.dto.request.CreateOrderRequest;
import com.ticketya.order.dto.response.OrderResponse;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.mapper.OrderMapper;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Paso 1 de la saga: crear la orden con reserva temporal.
 * <ol>
 *   <li>Idempotency-Key (400 si falta; misma clave + mismo pedido = misma orden; otro pedido = 422).</li>
 *   <li>catalog: evento comprable y precios (fuera de la transacción: no retiene conexión de base).</li>
 *   <li>Transacción única: lock por clave, cuota (UPDATE condicional), hold en inventory, persistir orden.
 *       Si algo falla se revierte TODO, incluida la cuota: no hay cuota que "compensar" a mano.</li>
 * </ol>
 */
@Service
public class CreateOrderService {

    public record CreateResult(OrderResponse body, boolean created) {}

    private static final Logger log = LoggerFactory.getLogger(CreateOrderService.class);

    private final CatalogPort catalog;
    private final InventoryPort inventory;
    private final OrderRepository orderRepo;
    private final OrderPlanner planner;
    private final RequestHasher hasher;
    private final QuotaService quota;
    private final IdempotencyLock lock;
    private final HoldReleaser holdReleaser;
    private final OrderMapper mapper;
    private final OrderProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CreateOrderService(CatalogPort catalog, InventoryPort inventory, OrderRepository orderRepo,
                              OrderPlanner planner, RequestHasher hasher, QuotaService quota, IdempotencyLock lock,
                              HoldReleaser holdReleaser, OrderMapper mapper, OrderProperties props,
                              TransactionTemplate tx, Clock clock) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.orderRepo = orderRepo;
        this.planner = planner;
        this.hasher = hasher;
        this.quota = quota;
        this.lock = lock;
        this.holdReleaser = holdReleaser;
        this.mapper = mapper;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    public CreateResult create(Identity caller, String idempotencyKey, CreateOrderRequest req) {
        caller.requireAnyRole(Role.CUSTOMER);
        String key = IdempotencyKey.require(idempotencyKey);
        String hash = hasher.hash(req);

        // Reintento: se responde sin tocar catalog ni inventory
        Optional<PurchaseOrder> existing = orderRepo.findByCustomerIdAndIdempotencyKey(caller.userId(), key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }

        CatalogPort.SaleInfo info = catalog.getSaleInfo(req.eventId());
        if (!"PUBLISHED".equals(info.status()) || !info.purchasable()) {
            throw OrderException.eventoNoComprable("El evento " + req.eventId() + " no está a la venta (" + info.status() + ")");
        }
        OrderPlan plan = planner.plan(req, info);

        return tx.execute(status -> createInTransaction(caller, key, hash, req, info, plan));
    }

    private CreateResult createInTransaction(Identity caller, String key, String hash, CreateOrderRequest req,
                                             CatalogPort.SaleInfo info, OrderPlan plan) {
        // Dos solicitudes con la misma clave: la segunda espera acá y después ve la orden ya creada
        lock.acquire("order", caller.userId() + ":" + key);
        Optional<PurchaseOrder> raced = orderRepo.findByCustomerIdAndIdempotencyKey(caller.userId(), key);
        if (raced.isPresent()) {
            return replay(raced.get(), hash);
        }

        UUID orderId = UUID.randomUUID();
        quota.consume(caller.userId(), req.eventId(), plan.quantity());              // 422 PURCHASE_LIMIT_EXCEEDED
        HoldInfo hold = inventory.createHold(req.eventId(), orderId, props.holdTtlSeconds(), plan.stockItems());
        try {
            PurchaseOrder order = PurchaseOrder.create(orderId, caller.userId(), req.eventId(), info.startsAt(),
                    plan.currency(), hold.holdId(), hold.expiresAt(), key, hash, plan.snapshots(hold.seatLabels()), clock.instant());
            orderRepo.saveAndFlush(order);
            log.info("order created orderId={} items={} total={}", orderId, plan.quantity(), order.getTotal());
            return new CreateResult(mapper.toResponse(order), true);
        } catch (RuntimeException e) {
            holdReleaser.releaseQuietly(hold.holdId());       // la orden no se creó: no dejar lugares retenidos
            throw e;
        }
    }

    private CreateResult replay(PurchaseOrder existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) {
            throw OrderException.claveIdempotenciaReutilizada();
        }
        return new CreateResult(mapper.toResponse(existing), false);
    }
}
