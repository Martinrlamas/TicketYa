package com.ticketya.platform.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce toda excepción a RFC 7807 (application/problem+json) con {@code code} y {@code correlationId}.
 * Tiene la MENOR precedencia: los {@code @RestControllerAdvice} de cada servicio (para sus excepciones de
 * negocio) se evalúan antes y este resuelve todo lo demás, incluidas las excepciones estándar de Spring MVC.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ------------------------------------------------------------------ errores propios

    /** Errores de negocio de cualquier servicio ({@link ApiException} y subclases). */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.warn("{}: {}", ex.getCode(), ex.getMessage());
        }
        return problem(ProblemFactory.build(ex.getStatus(), ex.getCode(), ex.getTitle(), ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::describe)
                .sorted()
                .collect(Collectors.joining("; "));
        return problem(ProblemFactory.of(ErrorCode.VALIDATION_ERROR, detail));
    }

    /** Timeout o caída de un servicio destino que no pasó por el RestClient de la plataforma. */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Object> handleUpstreamIo(ResourceAccessException ex) {
        log.warn("servicio destino no disponible: {}", ex.getMessage());
        return problem(ProblemFactory.of(ErrorCode.UPSTREAM_UNAVAILABLE, "Un servicio dependiente no responde"));
    }

    /** Última red: nunca se filtra el mensaje ni el stack trace al cliente. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("error no controlado", ex);
        return problem(ProblemFactory.build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno",
                "Ocurrió un error inesperado. Informar el correlationId."));
    }

    // ------------------------------------------------------------------ excepciones estándar de Spring MVC

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> errors.add(error(fe.getField(), fe.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(ge -> errors.add(error(ge.getObjectName(), ge.getDefaultMessage())));
        return validacion(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                           HttpHeaders headers, HttpStatusCode status,
                                                                           WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getAllValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(e -> errors.add(error(name != null ? name : "parameter", e.getDefaultMessage())));
        });
        return validacion(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return problem(ProblemFactory.of(ErrorCode.VALIDATION_ERROR, "El cuerpo del request es ilegible o tiene un formato inválido"));
    }

    /** Resto de las excepciones de Spring MVC (404, 405, 415, parámetros faltantes, tipos inválidos...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            String code = codeFor(status, statusCode.value());
            String title = status == null ? pd.getTitle() : titleFor(code, status);
            ProblemFactory.enrich(pd, code, title);
        }
        return response;
    }

    // ------------------------------------------------------------------ helpers

    private ResponseEntity<Object> validacion(List<Map<String, String>> errors) {
        String detail = errors.isEmpty()
                ? "El request no es válido"
                : errors.stream().map(e -> e.get("field") + ": " + e.get("message")).collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemFactory.of(ErrorCode.VALIDATION_ERROR, detail);
        pd.setProperty("errors", errors);
        return problem(pd);
    }

    private static ResponseEntity<Object> problem(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    private static Map<String, String> error(String field, String message) {
        Map<String, String> e = new LinkedHashMap<>();
        e.put("field", field);
        e.put("message", message == null ? "inválido" : message);
        return e;
    }

    private static String describe(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return (dot >= 0 ? path.substring(dot + 1) : path) + ": " + v.getMessage();
    }

    private static String codeFor(HttpStatus status, int raw) {
        if (status == null) {
            return raw >= 500 ? "INTERNAL_ERROR" : "VALIDATION_ERROR";
        }
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.VALIDATION_ERROR.name();
            case NOT_FOUND -> ErrorCode.NOT_FOUND.name();
            case SERVICE_UNAVAILABLE -> ErrorCode.UPSTREAM_UNAVAILABLE.name();
            case INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR";
            default -> status.name();                      // METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE, NOT_ACCEPTABLE ...
        };
    }

    private static String titleFor(String code, HttpStatus status) {
        return ErrorCode.find(code).map(ErrorCode::title).orElse(status.getReasonPhrase());
    }
}
