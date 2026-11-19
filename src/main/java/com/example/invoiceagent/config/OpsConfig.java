package com.example.invoiceagent.config;

import com.example.invoiceagent.ops.extraction.GroundTruthReplayExtractor;
import com.example.invoiceagent.ops.extraction.InvoiceExtractor;
import com.example.invoiceagent.ops.extraction.SpringAiInvoiceExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(OpsProps.class)
public class OpsConfig {

    @Bean
    @ConditionalOnProperty(prefix = "ops", name = "extractor", havingValue = "claude")
    InvoiceExtractor claudeInvoiceExtractor(AnthropicChatModel model) {
        return new SpringAiInvoiceExtractor("Claude (" + model.getDefaultOptions().getModel() + ")", model);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ops", name = "extractor", havingValue = "gemini")
    InvoiceExtractor geminiInvoiceExtractor(GoogleGenAiChatModel model) {
        return new SpringAiInvoiceExtractor("Gemini (" + model.getDefaultOptions().getModel() + ")", model);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ops", name = "extractor", havingValue = "replay", matchIfMissing = true)
    InvoiceExtractor replayInvoiceExtractor(OpsProps props, ObjectMapper mapper) {
        return new GroundTruthReplayExtractor(Path.of(props.getSamplesDir(), "ground_truth.json"), mapper);
    }
}
