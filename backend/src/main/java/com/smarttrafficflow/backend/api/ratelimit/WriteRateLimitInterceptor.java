package com.smarttrafficflow.backend.api.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.api.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits POST requests per client IP within fixed one-minute windows.
 * <p>
 * The client IP is {@code request.getRemoteAddr()}, and this class never reads {@code X-Forwarded-For}:
 * when the API is reached directly, any client could forge that header to dodge the limit.
 * Behind a reverse proxy (the web container) the server itself resolves the real client address, and only when
 * {@code APP_CLIENT_IP_HEADER} is set (see {@code server.tomcat.remoteip} in application.yml). That is safe only
 * while the proxy is the sole way to reach the API, as in the Compose file, where the backend publishes no port.
 * <p>
 * Counters live in memory, so each application instance enforces its own limit.
 */
public class WriteRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WriteRateLimitInterceptor.class);
    private static final long WINDOW_MILLIS = 60_000;

    private final String route;
    private final int requestsPerMinute;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile Window window = new Window(Long.MIN_VALUE, new ConcurrentHashMap<>());

    public WriteRateLimitInterceptor(String route, int requestsPerMinute, ObjectMapper objectMapper, Clock clock) {
        this.route = route;
        this.requestsPerMinute = requestsPerMinute;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }

        long now = clock.millis();
        String clientIp = request.getRemoteAddr();
        int requests = currentWindow(now / WINDOW_MILLIS).counts()
                .computeIfAbsent(clientIp, ip -> new AtomicInteger())
                .incrementAndGet();
        if (requests <= requestsPerMinute) {
            return true;
        }

        long retryAfterSeconds = Math.max(1, (WINDOW_MILLIS - now % WINDOW_MILLIS + 999) / 1000);
        log.warn("Rate limit exceeded on POST {} for client={} ({} requests in the current minute)",
                route, clientIp, requests);
        writeTooManyRequests(response, retryAfterSeconds);
        return false;
    }

    private Window currentWindow(long minute) {
        Window current = window;
        if (current.minute() == minute) {
            return current;
        }
        synchronized (this) {
            if (window.minute() != minute) {
                window = new Window(minute, new ConcurrentHashMap<>());
            }
            return window;
        }
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        ApiErrorResponse body = new ApiErrorResponse(
                "RATE_LIMITED",
                "Limite de requisicoes excedido. Tente novamente em instantes.",
                List.of(),
                OffsetDateTime.now(clock)
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private record Window(long minute, ConcurrentHashMap<String, AtomicInteger> counts) {
    }
}
