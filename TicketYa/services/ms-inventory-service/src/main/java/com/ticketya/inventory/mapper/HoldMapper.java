package com.ticketya.inventory.mapper;

import com.ticketya.inventory.dto.response.ConfirmHoldResponse;
import com.ticketya.inventory.dto.response.HoldResponse;
import com.ticketya.inventory.entity.EventSeat;
import com.ticketya.inventory.entity.Hold;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HoldMapper {

    public HoldResponse toResponse(Hold hold, List<EventSeat> seats) {
        var held = seats.stream()
                .map(s -> new HoldResponse.HeldSeat(s.getId(), s.getSectorId(), s.label()))
                .toList();
        return new HoldResponse(hold.getId(), hold.getOrderId(), hold.getStatus(), hold.getExpiresAt(), held);
    }

    public ConfirmHoldResponse toConfirmResponse(Hold hold) {
        return new ConfirmHoldResponse(hold.getId(), hold.getStatus());
    }
}
