package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.StreetResponse;
import com.smarttrafficflow.backend.api.dto.StreetSearchResponse;
import com.smarttrafficflow.backend.api.exception.GlobalExceptionHandler;
import com.smarttrafficflow.backend.domain.streets.service.StreetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StreetController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("StreetController MVC integration tests")
class StreetControllerITTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreetService streetService;

    @Test
    @DisplayName("does not expose an endpoint that lists every street")
    void doesNotListEveryStreet() throws Exception {
        mockMvc.perform(get("/api/streets"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("searches streets using the current paging payload")
    void searchesStreets() throws Exception {
        when(streetService.search(" avenida ", 5, 10)).thenReturn(new StreetSearchResponse(
                List.of(new StreetResponse(UUID.randomUUID(), 101L, "Avenida Central")),
                5,
                10,
                1
        ));

        mockMvc.perform(get("/api/streets/search")
                        .param("q", " avenida ")
                        .param("limit", "5")
                        .param("offset", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Avenida Central"))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.offset").value(10))
                .andExpect(jsonPath("$.total").value(1));

        verify(streetService).search(" avenida ", 5, 10);
    }
}
