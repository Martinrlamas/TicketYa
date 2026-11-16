package com.ticketya.catalog.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import com.ticketya.catalog.entity.EventType;
import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;

/**
 * Criterios de búsqueda. Se autovalida al construirse: todos los problemas salen como 400 VALIDATION_ERROR.
 * Defaults: page=0, size=20 (máx 100), sort=date, order=asc.
 */
public record EventSearchCriteria(
        EventType type,
        UUID organizerId,
        String participant,
        String city,
        LocalDate dateFrom,
        LocalDate dateTo,
        String q,
        String sort,
        String order,
        int page,
        int size) {

    public static final int MAX_SIZE = 100;

    public EventSearchCriteria {
        sort = sort == null || sort.isBlank() ? "date" : sort.trim().toLowerCase();
        order = order == null || order.isBlank() ? "asc" : order.trim().toLowerCase();
        if (!sort.equals("date") && !sort.equals("price") && !sort.equals("relevance")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "sort debe ser date, price o relevance");
        }
        if (!order.equals("asc") && !order.equals("desc")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "order debe ser asc o desc");
        }
        if (page < 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "page debe ser >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "size debe estar entre 1 y " + MAX_SIZE);
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "dateFrom no puede ser posterior a dateTo");
        }
    }
}
