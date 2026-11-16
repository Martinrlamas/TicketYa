package com.ticketya.platform.error;

/** Error con código del catálogo. Cualquier servicio puede lanzarlo; el GlobalExceptionHandler lo traduce. */
public class PlatformException extends ApiException {

    private final ErrorCode errorCode;

    public PlatformException(ErrorCode code, String detail) {
        super(code, detail);
        this.errorCode = code;
    }

    public PlatformException(ErrorCode code, String detail, Throwable cause) {
        super(code, detail, cause);
        this.errorCode = code;
    }

    /** El código como enum del catálogo (para comparar sin strings). */
    public ErrorCode code() { return errorCode; }
}
