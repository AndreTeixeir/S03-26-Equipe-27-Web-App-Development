package com.smarttrafficflow.backend.config;

import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@DisplayName("API docs exposure outside the dev profile")
class ApiDocsDisabledByDefaultITTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("does not publish the OpenAPI document")
    void doesNotPublishOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("does not publish the Swagger UI")
    void doesNotPublishSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
