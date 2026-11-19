package com.example.invoiceagent.config;

import com.example.invoiceagent.TestConfig;
import com.example.invoiceagent.ops.ai.UsageAdvisor;
import com.example.invoiceagent.ops.extraction.SpringAiInvoiceExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class AiConfigTest {

    @Autowired
    SpringAiInvoiceExtractor extractor;

    @Autowired
    List<Advisor> advisors;

    @Autowired
    ChatModel model;

    @Test
    void extractorUsesTheAutoConfiguredModel() {
        assertThat(extractor.getLabel()).isEqualTo(model.getDefaultOptions().getModel()).isNotBlank();
    }

    @Test
    void usageAdvisorIsRegistered() {
        assertThat(advisors).hasAtLeastOneElementOfType(UsageAdvisor.class);
    }
}
