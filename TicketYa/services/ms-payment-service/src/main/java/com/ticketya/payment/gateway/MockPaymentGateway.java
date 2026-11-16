package com.ticketya.payment.gateway;

import com.ticketya.payment.config.PaymentProperties;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pasarela simulada, determinista por token:
 * <ul>
 *   <li>{@code tok_approved} (y cualquier otro token) => aprobado</li>
 *   <li>{@code tok_rejected} => rechazado</li>
 *   <li>{@code tok_timeout} => duerme más que el timeout del cliente y luego aprueba (el pago queda
 *       persistido: order-service lo reconcilia con GET /internal/payments?orderId=)</li>
 *   <li>{@code tok_flaky} => falla la primera vez (por idempotencyKey) y aprueba al reintentar</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private final PaymentProperties props;
    private final Map<String, AtomicInteger> flakyAttempts = new ConcurrentHashMap<>();

    public MockPaymentGateway(PaymentProperties props) {
        this.props = props;
    }

    @Override
    public GatewayResult charge(GatewayChargeRequest request) {
        String ref = "mock-" + UUID.randomUUID().toString().substring(0, 8);
        return switch (request.token()) {
            case "tok_rejected" -> new GatewayResult(false, ref);
            case "tok_timeout" -> {
                sleep();
                yield new GatewayResult(true, ref);
            }
            case "tok_flaky" -> {
                int attempt = flakyAttempts.computeIfAbsent(request.idempotencyKey(), k -> new AtomicInteger())
                        .incrementAndGet();
                if (attempt == 1) {
                    throw new GatewayUnavailableException("La pasarela simulada falló (tok_flaky, primer intento)");
                }
                yield new GatewayResult(true, ref);
            }
            default -> new GatewayResult(true, ref);   // tok_approved y cualquier otro token
        };
    }

    @Override
    public GatewayResult refund(GatewayRefundRequest request) {
        return new GatewayResult(true, "mock-refund-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private void sleep() {
        try {
            Thread.sleep(props.mock().timeoutSimulation().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayUnavailableException("Interrumpido durante la simulación de timeout");
        }
    }
}
