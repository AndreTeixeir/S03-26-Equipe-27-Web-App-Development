package com.smarttrafficflow.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.api.ratelimit.WriteRateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private static final String SIMULATIONS_ROUTE = "/api/simulations/generate";
    private static final String TRAFFIC_RECORDS_ROUTE = "/api/traffic-records";

    private final WriteRateLimitInterceptor simulationsLimit;
    private final WriteRateLimitInterceptor trafficRecordsLimit;

    public RateLimitConfig(
            @Value("${app.rate-limit.simulations-per-minute:5}") int simulationsPerMinute,
            @Value("${app.rate-limit.traffic-records-per-minute:30}") int trafficRecordsPerMinute,
            ObjectMapper objectMapper
    ) {
        Clock clock = Clock.systemUTC();
        this.simulationsLimit = new WriteRateLimitInterceptor(
                SIMULATIONS_ROUTE, simulationsPerMinute, objectMapper, clock);
        this.trafficRecordsLimit = new WriteRateLimitInterceptor(
                TRAFFIC_RECORDS_ROUTE, trafficRecordsPerMinute, objectMapper, clock);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(simulationsLimit).addPathPatterns(SIMULATIONS_ROUTE);
        registry.addInterceptor(trafficRecordsLimit).addPathPatterns(TRAFFIC_RECORDS_ROUTE);
    }
}
