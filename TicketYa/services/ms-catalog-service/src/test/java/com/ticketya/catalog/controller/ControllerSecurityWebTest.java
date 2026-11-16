package com.ticketya.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ticketya.catalog.dto.response.EventResponse;
import com.ticketya.catalog.dto.response.InternalStatusResponse;
import com.ticketya.platform.web.PageResponse;
import com.ticketya.catalog.dto.response.SaleInfoResponse;
import com.ticketya.catalog.dto.response.VenueResponse;
import com.ticketya.catalog.entity.EventStatus;
import com.ticketya.catalog.entity.EventType;
import com.ticketya.catalog.service.EventSearchService;
import com.ticketya.catalog.service.EventService;
import com.ticketya.catalog.service.VenueService;
import com.ticketya.platform.config.TicketyaPlatformAutoConfiguration;

/** Roles por endpoint, token interno y formato de error. Los services van mockeados. */
@WebMvcTest({EventController.class, VenueController.class, InternalEventController.class})
@ImportAutoConfiguration(TicketyaPlatformAutoConfiguration.class)
@TestPropertySource(properties = "ticketya.platform.internal-token=secret")
class ControllerSecurityWebTest {

    static final UUID ID = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockitoBean EventService eventService;
    @MockitoBean EventSearchService searchService;
    @MockitoBean VenueService venueService;

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String role) {
        return b.header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", role);
    }

    private static EventResponse event(EventStatus status) {
        return new EventResponse(ID, "Show", null, EventType.CONCERT, UUID.randomUUID(), UUID.randomUUID(), "Tandil",
                OffsetDateTime.now().plusDays(10), status, List.of(), List.of(), BigDecimal.TEN, OffsetDateTime.now());
    }

    @Test
    void adminOperationsRejectCustomerAndOrganizer() throws Exception {
        for (String op : List.of("suspend", "resume", "cancel")) {
            for (String role : List.of("CUSTOMER", "ORGANIZER")) {
                mvc.perform(as(post("/api/events/{id}/" + op, ID).contentType(MediaType.APPLICATION_JSON).content("{}"), role))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
        }
    }

    @Test
    void adminCanSuspend() throws Exception {
        when(eventService.suspend(any())).thenReturn(event(EventStatus.SUSPENDED));
        mvc.perform(as(post("/api/events/{id}/suspend", ID), "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void organizerOperationsRequireIdentityAndRole() throws Exception {
        mvc.perform(post("/api/events/{id}/publish", ID)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(as(post("/api/events/{id}/publish", ID), "CUSTOMER")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/events/mine"), "CUSTOMER")).andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCreateVenues() throws Exception {
        mvc.perform(as(post("/api/venues").contentType(MediaType.APPLICATION_JSON).content("{}"), "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void venueWithInvalidLatitudeIsValidationError() throws Exception {
        String body = """
                {"name":"X","address":"Y","city":"Z","latitude":95,"longitude":10}""";
        mvc.perform(as(post("/api/venues").contentType(MediaType.APPLICATION_JSON).content(body), "ORGANIZER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void venueCreationReturns201WithLocation() throws Exception {
        when(venueService.create(any())).thenReturn(new VenueResponse(ID, "Estadio", "Av 1", "Tandil",
                new BigDecimal("-37.3217"), new BigDecimal("-59.1332"), List.of()));
        String body = """
                {"name":"Estadio","address":"Av 1","city":"Tandil","latitude":-37.3217,"longitude":-59.1332}""";
        mvc.perform(as(post("/api/venues").contentType(MediaType.APPLICATION_JSON).content(body), "ORGANIZER"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/venues/" + ID));
    }

    @Test
    void venueDetailIsPublic() throws Exception {
        when(venueService.get(any())).thenReturn(new VenueResponse(ID, "Estadio", "Av 1", "Tandil",
                BigDecimal.ONE, BigDecimal.ONE, List.of()));
        mvc.perform(get("/api/venues/{id}", ID)).andExpect(status().isOk());
    }

    @Test
    void searchIsPublicButRejectsOversizedPages() throws Exception {
        when(searchService.search(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/events")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/events").param("size", "500")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/events").param("type", "NOPE")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/events").param("dateFrom", "ayer")).andExpect(status().isBadRequest());
    }

    @Test
    void eventDetailIsPublicAndMalformedIdIs400() throws Exception {
        when(eventService.obtenerVisible(any(), any())).thenReturn(event(EventStatus.PUBLISHED));
        mvc.perform(get("/api/events/{id}", ID)).andExpect(status().isOk());
        mvc.perform(get("/api/events/{id}", "no-es-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void internalEndpointsRequireToken() throws Exception {
        when(eventService.saleInfo(any())).thenReturn(new SaleInfoResponse(ID, EventStatus.PUBLISHED, true,
                OffsetDateTime.now().plusDays(1), UUID.randomUUID(), List.of()));
        mvc.perform(get("/internal/events/{id}/sale-info", ID)).andExpect(status().isForbidden());
        mvc.perform(get("/internal/events/{id}/sale-info", ID).header("X-Internal-Token", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasable").value(true));
    }

    @Test
    void internalStatusAcceptsOnlyKnownStatuses() throws Exception {
        when(eventService.applyInventoryStatus(any(), any()))
                .thenReturn(new InternalStatusResponse(ID, EventStatus.SOLD_OUT));
        mvc.perform(put("/internal/events/{id}/status", ID).header("X-Internal-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SOLD_OUT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SOLD_OUT"));
        mvc.perform(put("/internal/events/{id}/status", ID).header("X-Internal-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BANANA\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/internal/events/{id}/status", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOLD_OUT\"}"))
                .andExpect(status().isForbidden());
    }
}
