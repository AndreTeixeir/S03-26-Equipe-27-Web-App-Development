package com.smarttrafficflow.backend.config;

import com.smarttrafficflow.backend.api.controller.SimulationController;
import com.smarttrafficflow.backend.api.controller.StreetController;
import com.smarttrafficflow.backend.domain.simulations.service.SimulationService;
import com.smarttrafficflow.backend.domain.streets.service.StreetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({StreetController.class, SimulationController.class})
@ActiveProfiles("test")
@DisplayName("CORS configuration MVC integration tests")
class CorsConfigITTests {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreetService streetService;

    @MockitoBean
    private SimulationService simulationService;

    @Test
    @DisplayName("allows GET from a configured origin without allowing credentials")
    void allowsGetWithoutCredentials() throws Exception {
        mockMvc.perform(options("/api/streets/search")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("allows POST from a configured origin")
    void allowsPost() throws Exception {
        mockMvc.perform(options("/api/simulations/generate")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("rejects methods the API does not use")
    void rejectsUnusedMethods() throws Exception {
        mockMvc.perform(options("/api/simulations/generate")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rejects origins that are not configured")
    void rejectsUnknownOrigins() throws Exception {
        mockMvc.perform(options("/api/streets/search")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
