package com.ticketya.order.service;

import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.repository.PurchaseQuotaRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límite de entradas por cliente y evento. El consumo es un UPDATE condicional atómico; la fila del
 * contador queda bloqueada hasta el commit, de modo que las órdenes simultáneas del mismo cliente se serializan.
 * Siempre corre dentro de la transacción del llamador (si esa transacción se revierte, la cuota también).
 */
@Component
public class QuotaService {

    private final PurchaseQuotaRepository quotaRepo;
    private final OrderProperties props;

    public QuotaService(PurchaseQuotaRepository quotaRepo, OrderProperties props) {
        this.quotaRepo = quotaRepo;
        this.props = props;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(UUID customerId, UUID eventId, int quantity) {
        quotaRepo.insertIfAbsent(customerId, eventId);
        if (quotaRepo.tryConsume(customerId, eventId, quantity, props.purchaseLimit()) == 0) {
            throw OrderException.limiteCompraExcedido(props.purchaseLimit());
        }
    }

    /** Libera lo que consumía una orden completa (rechazo, expiración, cancelación, reintegro por falla). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(PurchaseOrder order) {
        release(order.getCustomerId(), order.getEventId(), order.quantity());
    }

    /** Se libera al rechazar, expirar, cancelar o devolver. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID customerId, UUID eventId, int quantity) {
        quotaRepo.release(customerId, eventId, quantity);
    }
}
