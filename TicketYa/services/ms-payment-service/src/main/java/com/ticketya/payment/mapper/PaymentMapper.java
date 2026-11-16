package com.ticketya.payment.mapper;

import com.ticketya.payment.dto.response.PaymentResponse;
import com.ticketya.payment.dto.response.RefundResponse;
import com.ticketya.payment.entity.Payment;
import com.ticketya.payment.entity.Refund;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getCustomerId(), p.getAmount(), p.getCurrency(),
                p.getMethod(), p.getLast4(), p.getStatus(), p.getGatewayRef(), p.getRefundedAmount(), p.getCreatedAt());
    }

    public RefundResponse toResponse(Refund r) {
        return new RefundResponse(r.getId(), r.getPaymentId(), r.getAmount(), r.getStatus(), r.getCreatedAt());
    }
}
