package com.ticketya.inventory.controller;

import com.ticketya.inventory.dto.request.CreateHoldRequest;
import com.ticketya.inventory.dto.response.ConfirmHoldResponse;
import com.ticketya.inventory.dto.response.HoldResponse;
import com.ticketya.inventory.mapper.HoldMapper;
import com.ticketya.inventory.service.HoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/holds")
@Tag(name = "Holds (interno)")
public class HoldController {

    private final HoldService holdService;
    private final HoldMapper mapper;

    public HoldController(HoldService holdService, HoldMapper mapper) {
        this.holdService = holdService;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Crear hold (todo-o-nada, idempotente por orderId)",
            description = "201 si lo creó; 200 si ya existía un hold para esa orden. 409: SEAT_UNAVAILABLE, INSUFFICIENT_CAPACITY o EVENT_NOT_PURCHASABLE.")
    public ResponseEntity<HoldResponse> create(@Valid @RequestBody CreateHoldRequest request) {
        var result = holdService.create(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(mapper.toResponse(result.hold(), result.seats()));
    }

    @PostMapping("/{holdId}/confirm")
    @Operation(summary = "Confirmar venta del hold (idempotente)", description = "410 HOLD_EXPIRED si venció (los lugares se liberan).")
    public ConfirmHoldResponse confirm(@PathVariable UUID holdId) {
        return mapper.toConfirmResponse(holdService.confirm(holdId));
    }

    @DeleteMapping("/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Liberar hold (idempotente)")
    public void release(@PathVariable UUID holdId) {
        holdService.release(holdId);
    }
}
