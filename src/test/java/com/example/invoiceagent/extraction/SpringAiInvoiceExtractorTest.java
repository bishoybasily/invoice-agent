package com.example.invoiceagent.extraction;

import com.example.invoiceagent.model.dto.ExtractedInvoice;
import com.example.invoiceagent.ops.extraction.SpringAiInvoiceExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiInvoiceExtractorTest {

    static final String ANSWER = """
            {"seller":{"name":"Muster Bürobedarf GmbH","street":"Beispielstraße 12","city":"70173 Stuttgart",
             "vatId":"DE123456789","iban":"DE02 1203 0000 0000 2020 51"},
             "buyer":{"name":"Testkunde Logistik AG","street":"Hafenweg 3","city":"20457 Hamburg","vatId":"","iban":""},
             "invoiceNumber":"RE-2026-1001","invoiceDate":"2026-11-02","dueDate":"2026-12-02","orderReference":"PO-88231",
             "buyerReference":"04011000-12345-34","currency":"EUR",
             "lines":[{"description":"Kopierpapier","quantity":"4","unitPrice":"29.90","netAmount":"119.60","vatRate":"19"}],
             "vatBreakdown":[{"vatRate":"19","taxableAmount":"119.60","vatAmount":"22.72"}],
             "netTotal":"119.60","vatTotal":"22.72","grossTotal":"142.32","note":"","confidence":0.97,"uncertainFields":[]}
            """;

    @Test
    void sendsThePdfAndSkipsThinkingBlocks() throws Exception {
        AtomicReference<Prompt> seen = new AtomicReference<>();
        ChatModel fake = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                seen.set(prompt);
                var thinking = new Generation(AssistantMessage.builder()
                        .content("let me look at the totals...").properties(Map.of("signature", "abc")).build());
                var answer = new Generation(new AssistantMessage("```json\n" + ANSWER + "\n```"));
                return new ChatResponse(List.of(thinking, answer));
            }
        };

        byte[] pdf = Files.readAllBytes(Path.of("samples/01_clean_de_single_vat.pdf"));
        ExtractedInvoice inv = new SpringAiInvoiceExtractor("Fake", fake).extract("01.pdf", pdf);

        assertThat(inv.invoiceNumber()).isEqualTo("RE-2026-1001");
        assertThat(inv.confidence()).isEqualTo(0.97);
        UserMessage user = (UserMessage) seen.get().getInstructions().stream()
                .filter(m -> m instanceof UserMessage).findFirst().orElseThrow();
        assertThat(user.getMedia()).hasSize(1);
        assertThat(user.getMedia().get(0).getMimeType().toString()).isEqualTo("application/pdf");
        assertThat(user.getText()).contains("invoiceNumber");
    }
}
