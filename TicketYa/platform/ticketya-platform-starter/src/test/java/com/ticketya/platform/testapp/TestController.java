package com.ticketya.platform.testapp;

import com.ticketya.platform.correlation.CorrelationId;
import com.ticketya.platform.error.ErrorCode;
import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.RequireRole;
import com.ticketya.platform.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    public record Body(@NotBlank String name, @Min(1) int quantity) {}

    @GetMapping("/api/public/ping")
    public Map<String, String> ping() {
        return Map.of("correlationId", String.valueOf(CorrelationId.current()));
    }

    @GetMapping("/api/secure/me")
    @RequireRole
    public Map<String, String> me(Identity identity) {
        return Map.of("userId", identity.userId().toString(), "role", identity.role().name());
    }

    @GetMapping("/api/admin/only")
    @RequireRole(Role.ADMIN)
    public Map<String, String> adminOnly() {
        return Map.of("ok", "true");
    }

    @GetMapping("/api/staff-or-admin")
    @RequireRole({Role.ADMIN, Role.ACCESS_STAFF})
    public Map<String, String> staffOrAdmin() {
        return Map.of("ok", "true");
    }

    @PostMapping("/api/validate")
    public Map<String, String> validate(@Valid @RequestBody Body body) {
        return Map.of("name", body.name());
    }

    @GetMapping("/api/param")
    public Map<String, Integer> param(@RequestParam @Min(1) int limit) {
        return Map.of("limit", limit);
    }

    @GetMapping("/api/boom")
    public String boom() {
        throw new IllegalStateException("detalle-secreto-interno");
    }

    @GetMapping("/api/domain-error")
    public String domainError() {
        throw new PlatformException(ErrorCode.SEAT_UNAVAILABLE, "El asiento A-5 ya no está disponible");
    }

    @GetMapping("/internal/ping")
    public Map<String, String> internalPing() {
        return Map.of("ok", "true");
    }
}
