package com.ticketya.order.mapper;

import com.ticketya.order.dto.response.RefundRequestResponse;
import com.ticketya.order.entity.RefundRequest;
import org.springframework.stereotype.Component;

@Component
public class RefundRequestMapper {

    public RefundRequestResponse toResponse(RefundRequest r) {
        return new RefundRequestResponse(r.getId(), r.getOrderId(), r.getOrderItemId(), r.getCustomerId(), r.getStatus(),
                r.getReason(), r.getCreatedAt(), r.getDecidedBy(), r.getDecidedAt(), r.isAutomatic(), r.getStep(),
                r.getAttempts(), r.getLastError());
    }
}
