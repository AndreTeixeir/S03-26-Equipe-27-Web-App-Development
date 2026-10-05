package com.smarttrafficflow.backend;

import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
class SmartTrafficFlowApplicationTests {

    @Test
    void contextLoads() {
    }
}
