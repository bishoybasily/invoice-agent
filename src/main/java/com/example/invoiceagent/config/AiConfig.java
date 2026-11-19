package com.example.invoiceagent.config;

import com.example.invoiceagent.ops.ai.UsageAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatModel chatModel, List<Advisor> advisors,
                          @Value("classpath:prompts/invoice-extraction-system.st") Resource systemPrompt) {
        return ChatClient.builder(chatModel).defaultSystem(systemPrompt).defaultAdvisors(advisors).build();
    }

    @Bean
    Advisor usageAdvisor() {
        return new UsageAdvisor();
    }
}
