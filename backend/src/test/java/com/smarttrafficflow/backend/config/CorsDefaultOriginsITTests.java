package com.smarttrafficflow.backend.config;

import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs without the test profile, which defines the allowed origins and would hide a missing default.
 * If APP_CORS_ALLOWED_ORIGINS is set in the environment running the tests, that value is used instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgisTestcontainersConfiguration.class)
@DisplayName("CORS default origins without APP_CORS_ALLOWED_ORIGINS")
class CorsDefaultOriginsITTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("starts and allows the local frontend origins by default")
    void allowsLocalFrontendOriginsByDefault() throws Exception {
        mockMvc.perform(options("/api/streets/search")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }
}
