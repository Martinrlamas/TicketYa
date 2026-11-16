package com.ticketya.gateway;

import com.ticketya.gateway.filter.IdentityWebFilterAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/** Tabla de rutas públicas: unitario, sin levantar Spring. */
class PublicRoutesTest {

    private static boolean pub(HttpMethod m, String path) { return IdentityWebFilterAccess.isPublic(m, path); }

    @Test void catalog_and_availability_reads_are_public() {
        assertThat(pub(HttpMethod.GET, "/api/events")).isTrue();
        assertThat(pub(HttpMethod.GET, "/api/events/9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d")).isTrue();
        assertThat(pub(HttpMethod.GET, "/api/events/9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d/availability")).isTrue();
        assertThat(pub(HttpMethod.GET, "/api/venues/5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10")).isTrue();
    }

    @Test void writes_and_private_reads_are_not_public() {
        assertThat(pub(HttpMethod.POST, "/api/events")).isFalse();
        assertThat(pub(HttpMethod.POST, "/api/venues")).isFalse();
        assertThat(pub(HttpMethod.GET, "/api/events/mine")).isFalse();
        assertThat(pub(HttpMethod.GET, "/api/orders")).isFalse();
        assertThat(pub(HttpMethod.GET, "/api/tickets")).isFalse();
        assertThat(pub(HttpMethod.GET, "/api/refund-requests")).isFalse();
        assertThat(pub(HttpMethod.PUT, "/api/events/abc/sector-prices")).isFalse();
    }

    @Test void preflight_is_public() { assertThat(pub(HttpMethod.OPTIONS, "/api/orders")).isTrue(); }

    @Test void identity_validation() {
        String ok = "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67";
        assertThat(IdentityWebFilterAccess.valid(ok, "ADMIN")).isTrue();
        assertThat(IdentityWebFilterAccess.valid(ok, "admin")).isFalse();
        assertThat(IdentityWebFilterAccess.valid(ok, null)).isFalse();
        assertThat(IdentityWebFilterAccess.valid("x", "ADMIN")).isFalse();
    }
}
