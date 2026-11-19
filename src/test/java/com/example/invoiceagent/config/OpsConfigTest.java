package com.example.invoiceagent.config;

import com.example.invoiceagent.ops.extraction.GroundTruthReplayExtractor;
import com.example.invoiceagent.ops.extraction.InvoiceExtractor;
import com.example.invoiceagent.ops.extraction.SpringAiInvoiceExtractor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

class OpsConfigTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class Replay {

        @Autowired
        InvoiceExtractor extractor;

        @Autowired
        ApplicationContext context;

        @Test
        void usesReplayWithoutAnyModel() {
            assertThat(extractor).isInstanceOf(GroundTruthReplayExtractor.class);
            assertThat(context.getBeanNamesForType(AnthropicChatModel.class)).isEmpty();
            assertThat(context.getBeanNamesForType(GoogleGenAiChatModel.class)).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(properties = {"ops.extractor=claude", "spring.ai.anthropic.api-key=test-key"})
    @ActiveProfiles("test")
    class Claude {

        @Autowired
        InvoiceExtractor extractor;

        @Autowired
        AnthropicChatProperties anthropic;

        @Autowired
        ApplicationContext context;

        @Test
        void usesClaudeWithoutTemperature() {
            assertThat(extractor).isInstanceOf(SpringAiInvoiceExtractor.class);
            assertThat(extractor.getLabel()).isEqualTo("Claude (claude-opus-5-5)");
            assertThat(anthropic.getOptions().getTemperature()).isNull();
            assertThat(context.getBeanNamesForType(GoogleGenAiChatModel.class)).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(properties = {"ops.extractor=gemini", "spring.ai.google.genai.api-key=test-key"})
    @ActiveProfiles("test")
    class Gemini {

        @Autowired
        InvoiceExtractor extractor;

        @Autowired
        ApplicationContext context;

        @Test
        void usesGemini() {
            assertThat(extractor).isInstanceOf(SpringAiInvoiceExtractor.class);
            assertThat(extractor.getLabel()).startsWith("Gemini (");
            assertThat(context.getBeanNamesForType(AnthropicChatModel.class)).isEmpty();
        }
    }
}
