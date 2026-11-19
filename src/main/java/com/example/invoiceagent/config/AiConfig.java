package com.example.invoiceagent.config;

import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
public class AiConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "ops", name = "extractor", havingValue = "claude")
    @Import(AnthropicChatAutoConfiguration.class)
    static class Claude {

        @Bean
        static BeanPostProcessor clearAnthropicTemperature() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof AnthropicChatProperties props) {
                        props.getOptions().setTemperature(null);
                    }
                    return bean;
                }
            };
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ops", name = "extractor", havingValue = "gemini")
    @Import(GoogleGenAiChatAutoConfiguration.class)
    static class Gemini {
    }
}
