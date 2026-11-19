package com.example.invoiceagent;

import com.example.invoiceagent.ops.extraction.GroundTruthReplayExtractor;
import com.example.invoiceagent.ops.extraction.InvoiceExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.MariaDBContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestConfig {

    @Bean
    @ServiceConnection
    MariaDBContainer<?> mariaDb() {
        return new MariaDBContainer<>("mariadb:11.4");
    }

    @Bean
    @Primary
    InvoiceExtractor replayExtractor(ObjectMapper mapper) {
        return new GroundTruthReplayExtractor(Samples.file("ground_truth.json"), mapper);
    }
}
