package com.example.invoiceagent.ops.extraction;

import com.example.invoiceagent.model.dto.ExtractedInvoice;
import com.example.invoiceagent.model.exception.ExtractionException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import lombok.Getter;

import java.util.Map;

public class SpringAiInvoiceExtractor implements InvoiceExtractor {

    static final String SYSTEM_PROMPT = """
            You read supplier invoices for an EDI pipeline. Your output is checked by deterministic
            validation rules afterwards, so your only job is to transcribe faithfully.

            - Copy every value exactly as printed. Never calculate, round or correct a number,
              even if the invoice is obviously wrong: a wrong total must stay wrong so the rules catch it.
            - Write amounts with a dot as decimal separator and no thousands separator
              (German "1.074,54 €" becomes "1074.54").
            - Write dates as yyyy-MM-dd.
            - If a field is not on the invoice, use an empty string. Do not guess or invent values.
            - If the document is a poor scan, still extract what you can, lower your confidence
              and list the fields you are unsure about.
            """;

    private static final MimeType PDF = MimeTypeUtils.parseMimeType("application/pdf");

    @Getter
    private final String label;
    private final ChatClient chatClient;
    private final BeanOutputConverter<ExtractedInvoice> converter = new BeanOutputConverter<>(ExtractedInvoice.class);

    public SpringAiInvoiceExtractor(String label, ChatModel chatModel) {
        this.label = label;
        this.chatClient = ChatClient.builder(chatModel).defaultSystem(SYSTEM_PROMPT).build();
    }

    @Override
    public ExtractedInvoice extract(String fileName, byte[] pdf) {
        ChatResponse response;
        try {
            response = chatClient.prompt()
                    .user(u -> u.text("Extract the invoice fields from the attached document ({file}).\n\n{format}")
                            .param("file", fileName)
                            .param("format", converter.getFormat())
                            .media(PDF, new ByteArrayResource(pdf) {
                                @Override
                                public String getFilename() {
                                    return fileName;
                                }
                            }))
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            throw new ExtractionException(label + " call failed: " + root.getMessage(), e);
        }
        if (response == null) {
            throw new ExtractionException(label + " returned no response");
        }

        String json = response.getResults().stream()
                .filter(SpringAiInvoiceExtractor::isAnswer)
                .map(g -> g.getOutput().getText())
                .reduce((first, second) -> second)
                .orElse(null);

        String finishReason = response.getResults().isEmpty() || response.getResult().getMetadata() == null
                ? "" : String.valueOf(response.getResult().getMetadata().getFinishReason()).toLowerCase();
        if (finishReason.contains("refusal") || finishReason.contains("safety")) {
            throw new ExtractionException(label + " declined to process this document (" + finishReason + ")");
        }
        if (json == null) {
            throw new ExtractionException(label + " returned no answer (finish reason: " + finishReason + ")");
        }
        try {
            return converter.convert(json);
        } catch (RuntimeException e) {
            throw new ExtractionException(label + " returned something that is not the expected JSON", e);
        }
    }

    private static boolean isAnswer(Generation g) {
        if (g.getOutput() == null || g.getOutput().getText() == null || g.getOutput().getText().isBlank()) {
            return false;
        }
        Map<String, Object> props = g.getOutput().getMetadata();
        return props == null || !(props.containsKey("signature") || props.containsKey("data"));
    }

}
