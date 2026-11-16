package com.ticketya.payment.gateway;

/**
 * Puerto hacia la pasarela de pagos. Hoy lo implementa {@link MockPaymentGateway}; un proveedor real
 * implica solo un nuevo adaptador (seleccionado con {@code payment.gateway}).
 * Un rechazo es un resultado normal ({@code approved=false}); una falla técnica lanza {@link GatewayUnavailableException}.
 */
public interface PaymentGateway {

    GatewayResult charge(GatewayChargeRequest request);

    GatewayResult refund(GatewayRefundRequest request);
}
