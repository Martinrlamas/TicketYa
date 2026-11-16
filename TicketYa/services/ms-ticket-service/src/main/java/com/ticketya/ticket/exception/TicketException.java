package com.ticketya.ticket.exception;

import com.ticketya.platform.error.ApiException;
import com.ticketya.platform.error.ErrorCode;

/** Error de negocio de ticket-service; el GlobalExceptionHandler del starter lo traduce a RFC 7807. */
public class TicketException extends ApiException {

    private TicketException(ErrorCode code, String detail) {
        super(code, detail);
    }

    public static TicketException noEncontrado(String detail) {
        return new TicketException(ErrorCode.NOT_FOUND, detail);
    }

    public static TicketException validacion(String detail) {
        return new TicketException(ErrorCode.VALIDATION_ERROR, detail);
    }

    public static TicketException estadoInvalido(String detail) {
        return new TicketException(ErrorCode.INVALID_STATE_TRANSITION, detail);
    }
}
