package com.ticketya.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionTest {

    /** Como las excepciones de negocio de cada servicio: heredan de ApiException. */
    static class ServiceException extends ApiException {
        ServiceException(ErrorCode code, String detail) {
            super(code, detail);
        }
    }

    @Test
    void takesStatusCodeAndTitleFromTheCatalog() {
        var ex = new ServiceException(ErrorCode.SEAT_UNAVAILABLE, "A-5 ocupado");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("SEAT_UNAVAILABLE");
        assertThat(ex.getTitle()).isEqualTo("Asiento no disponible");
        assertThat(ex.getMessage()).isEqualTo("A-5 ocupado");
        assertThat(ex.hasCode("SEAT_UNAVAILABLE")).isTrue();
    }

    @Test
    void platformExceptionExposesTheEnumCode() {
        assertThat(new PlatformException(ErrorCode.NOT_FOUND, "x").code()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void errorCodeLookupByName() {
        assertThat(ErrorCode.find("HOLD_EXPIRED")).contains(ErrorCode.HOLD_EXPIRED);
        assertThat(ErrorCode.find("NO_EXISTE")).isEmpty();
    }
}
