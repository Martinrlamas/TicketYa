package com.ticketya.inventory.exception;

import com.ticketya.platform.error.ApiException;
import com.ticketya.platform.error.ErrorCode;

/** Error de negocio del inventario; el GlobalExceptionHandler del starter lo traduce a RFC 7807. */
public class InventoryException extends ApiException {

    protected InventoryException(ErrorCode code, String detail) {
        super(code, detail);
    }

    public static InventoryException noEncontrado(String detail) {
        return new InventoryException(ErrorCode.NOT_FOUND, detail);
    }

    public static InventoryException validacion(String detail) {
        return new InventoryException(ErrorCode.VALIDATION_ERROR, detail);
    }

    public static InventoryException asientoNoDisponible(String detail) {
        return new InventoryException(ErrorCode.SEAT_UNAVAILABLE, detail);
    }

    public static InventoryException capacidadInsuficiente(String detail) {
        return new InventoryException(ErrorCode.INSUFFICIENT_CAPACITY, detail);
    }

    public static InventoryException eventoNoComprable(String detail) {
        return new InventoryException(ErrorCode.EVENT_NOT_PURCHASABLE, detail);
    }

    public static InventoryException estadoInvalido(String detail) {
        return new InventoryException(ErrorCode.INVALID_STATE_TRANSITION, detail);
    }

    public static InventoryException servicioNoDisponible(String detail) {
        return new InventoryException(ErrorCode.UPSTREAM_UNAVAILABLE, detail);
    }
}
