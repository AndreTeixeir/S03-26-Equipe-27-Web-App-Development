package com.smarttrafficflow.backend.api.exception;

import com.smarttrafficflow.backend.api.controller.StreetController;
import com.smarttrafficflow.backend.domain.streets.service.StreetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StreetController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("Unknown route MVC integration tests")
class UnknownRouteITTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreetService streetService;

    @Test
    @DisplayName("answers unknown API routes with 404 in the API error format")
    void answersUnknownRoutesWithNotFound() throws Exception {
        mockMvc.perform(get("/api/rota-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Recurso nao encontrado"));
    }
}
