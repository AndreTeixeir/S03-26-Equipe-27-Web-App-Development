package com.smarttrafficflow.backend.api.ratelimit;

import com.smarttrafficflow.backend.domain.simulations.service.SimulationService;
import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Talks to a real embedded Tomcat, because the client IP is resolved by the server before the interceptor runs.
 * Every request comes from the same TCP peer (the test client), like requests relayed by the web container (Caddy).
 */
@DisplayName("Client IP for the write rate limit")
class ClientIpBehindProxyITTests {

    private static final String SIMULATIONS_URL = "/api/simulations/generate";
    private static final String BODY = "{\"recordsToGenerate\":1,\"scenarioName\":\"Teste\"}";

    abstract static class RealServerSupport {

        @Autowired
        TestRestTemplate rest;

        @MockitoBean
        SimulationService simulationService;

        HttpStatusCode postSimulationAs(String forwardedFor) {
            when(simulationService.generate(any())).thenReturn(List.of());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("X-Forwarded-For", forwardedFor);
            return rest.exchange(SIMULATIONS_URL, HttpMethod.POST, new HttpEntity<>(BODY, headers), String.class)
                    .getStatusCode();
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"APP_CLIENT_IP_HEADER=X-Forwarded-For", "app.rate-limit.simulations-per-minute=2"})
    @ActiveProfiles("test")
    @Import(PostgisTestcontainersConfiguration.class)
    @DisplayName("with APP_CLIENT_IP_HEADER set (behind the web container)")
    class BehindProxy extends RealServerSupport {

        @Test
        @DisplayName("counts each real client separately instead of sharing the proxy address")
        void countsEachForwardedClientSeparately() {
            assertThat(postSimulationAs("203.0.113.10").value()).isEqualTo(200);
            assertThat(postSimulationAs("203.0.113.10").value()).isEqualTo(200);
            assertThat(postSimulationAs("203.0.113.10").value()).isEqualTo(429);

            assertThat(postSimulationAs("203.0.113.20").value()).isEqualTo(200);
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"app.rate-limit.simulations-per-minute=2"})
    @ActiveProfiles("test")
    @Import(PostgisTestcontainersConfiguration.class)
    @DisplayName("without APP_CLIENT_IP_HEADER (API reachable directly)")
    class WithoutProxy extends RealServerSupport {

        @Test
        @DisplayName("ignores X-Forwarded-For, so forging it does not dodge the limit")
        void ignoresForwardedHeader() {
            assertThat(postSimulationAs("203.0.113.10").value()).isEqualTo(200);
            assertThat(postSimulationAs("203.0.113.20").value()).isEqualTo(200);
            assertThat(postSimulationAs("203.0.113.30").value()).isEqualTo(429);
        }
    }
}
