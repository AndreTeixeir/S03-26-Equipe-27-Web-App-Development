package com.smarttrafficflow.backend.api.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WriteRateLimitInterceptor tests")
class WriteRateLimitInterceptorTests {

    private static final Instant START = Instant.parse("2024-06-17T08:00:10Z");

    private final MutableClock clock = new MutableClock(START);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final WriteRateLimitInterceptor interceptor =
            new WriteRateLimitInterceptor("/api/simulations/generate", 2, objectMapper, clock);

    @Test
    @DisplayName("allows requests up to the limit and rejects the next one with 429")
    void rejectsRequestsAboveLimit() throws Exception {
        assertThat(handle("POST", "198.51.100.1").getStatus()).isEqualTo(200);
        assertThat(handle("POST", "198.51.100.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = handle("POST", "198.51.100.1");

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("50");
        JsonNode body = objectMapper.readTree(rejected.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(body.get("message").asText()).isEqualTo("Limite de requisicoes excedido. Tente novamente em instantes.");
    }

    @Test
    @DisplayName("allows requests again when the next minute starts")
    void allowsRequestsInTheNextWindow() throws Exception {
        handle("POST", "198.51.100.1");
        handle("POST", "198.51.100.1");
        assertThat(handle("POST", "198.51.100.1").getStatus()).isEqualTo(429);

        clock.set(Instant.parse("2024-06-17T08:01:00Z"));

        assertThat(handle("POST", "198.51.100.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("reports at least one second in Retry-After at the end of a window")
    void reportsAtLeastOneSecondInRetryAfter() throws Exception {
        clock.set(Instant.parse("2024-06-17T08:00:59.900Z"));
        handle("POST", "198.51.100.1");
        handle("POST", "198.51.100.1");

        assertThat(handle("POST", "198.51.100.1").getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    @DisplayName("does not count requests that are not POST")
    void ignoresNonPostRequests() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(handle("GET", "198.51.100.1").getStatus()).isEqualTo(200);
        }

        assertThat(handle("POST", "198.51.100.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ignores X-Forwarded-For when identifying the client")
    void ignoresForwardedForHeader() throws Exception {
        handle("POST", "198.51.100.1");
        handle("POST", "198.51.100.1");

        MockHttpServletRequest request = request("POST", "198.51.100.1");
        request.addHeader("X-Forwarded-For", "192.0.2.99");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse handle(String method, String clientIp) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request(method, clientIp), response, new Object());
        return response;
    }

    private static MockHttpServletRequest request(String method, String clientIp) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/simulations/generate");
        request.setRemoteAddr(clientIp);
        return request;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
