package com.smarttrafficflow.backend.api.exception;

import com.smarttrafficflow.backend.api.controller.AnalyticsController;
import com.smarttrafficflow.backend.api.controller.TrafficRecordController;
import com.smarttrafficflow.backend.domain.analytics.service.AnalyticsService;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({TrafficRecordController.class, AnalyticsController.class})
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("Client errors are answered with 4xx in the ApiErrorResponse format")
class ClientErrorResponsesTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrafficRecordService trafficRecordService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("answers 400 when a parameter has the wrong type")
    void answers400ForParameterWithWrongType() throws Exception {
        mockMvc.perform(get("/api/traffic-records").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value(containsString("page")))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("answers 400 when the JSON body is malformed")
    void answers400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/traffic-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("JSON")))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("answers 400 when the request body is missing")
    void answers400ForMissingBody() throws Exception {
        mockMvc.perform(post("/api/traffic-records").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("answers 400 when a required parameter is missing")
    void answers400ForMissingRequiredParameter() throws Exception {
        mockMvc.perform(get("/api/traffic-stats"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value(containsString("groupBy")));
    }

    @Test
    @DisplayName("answers 400 when page is negative")
    void answers400ForNegativePage() throws Exception {
        mockMvc.perform(get("/api/traffic-records").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(containsString("page")));
    }

    @Test
    @DisplayName("answers 400 when size is zero")
    void answers400ForZeroSize() throws Exception {
        mockMvc.perform(get("/api/traffic-records").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(containsString("size")));
    }

    @Test
    @DisplayName("answers 405 with the Allow header when the method is not supported")
    void answers405WithAllowHeader() throws Exception {
        mockMvc.perform(put("/api/traffic-records"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value(containsString("PUT")));
    }

    @Test
    @DisplayName("answers 415 when the content type is not supported")
    void answers415ForUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/traffic-records")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value(containsString("text/plain")));
    }

    @Test
    @DisplayName("keeps 500 for real server errors without internal details in the response")
    void keeps500WithoutInternalDetails() throws Exception {
        when(trafficRecordService.findPage(anyInt(), anyInt(), any()))
                .thenThrow(new NullPointerException("detalhe interno secreto"));

        mockMvc.perform(get("/api/traffic-records"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString("NullPointerException"))))
                .andExpect(content().string(not(containsString("detalhe interno secreto"))));
    }
}
