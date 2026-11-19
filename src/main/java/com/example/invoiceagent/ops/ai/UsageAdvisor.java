package com.example.invoiceagent.ops.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

@Slf4j
public class UsageAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        var res = chain.nextCall(req);
        record(res);
        return res;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest req, StreamAdvisorChain chain) {
        return new ChatClientMessageAggregator().aggregateChatClientResponse(chain.nextStream(req), this::record);
    }

    private void record(ChatClientResponse res) {
        if (res.chatResponse() == null)
            return;
        var usage = res.chatResponse().getMetadata().getUsage();
        log.info("tokens prompt={} completion={} total={}", usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    @Override
    public String getName() {
        return "usageAdvisor";
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
