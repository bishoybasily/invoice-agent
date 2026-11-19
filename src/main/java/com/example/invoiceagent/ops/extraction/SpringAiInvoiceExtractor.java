package com.example.invoiceagent.ops.extraction;

import com.example.invoiceagent.model.dto.ExtractedInvoice;
import com.example.invoiceagent.model.exception.ExtractionException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SpringAiInvoiceExtractor implements InvoiceExtractor {

    private static final MimeType PDF = MimeTypeUtils.parseMimeType("application/pdf");

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final BeanOutputConverter<ExtractedInvoice> converter = new BeanOutputConverter<>(ExtractedInvoice.class);

    @Override
    public String getLabel() {
        return chatModel.getDefaultOptions().getModel();
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
            throw new ExtractionException(getLabel() + " call failed: " + root.getMessage(), e);
        }
        if (response == null) {
            throw new ExtractionException(getLabel() + " returned no response");
        }

        String json = response.getResults().stream()
                .filter(SpringAiInvoiceExtractor::isAnswer)
                .map(g -> g.getOutput().getText())
                .reduce((first, second) -> second)
                .orElse(null);

        String finishReason = response.getResults().isEmpty()
                ? "" : String.valueOf(response.getResult().getMetadata().getFinishReason()).toLowerCase();
        if (finishReason.contains("refusal") || finishReason.contains("safety")) {
            throw new ExtractionException(getLabel() + " declined to process this document (" + finishReason + ")");
        }
        if (json == null) {
            throw new ExtractionException(getLabel() + " returned no answer (finish reason: " + finishReason + ")");
        }
        try {
            return converter.convert(json);
        } catch (RuntimeException e) {
            throw new ExtractionException(getLabel() + " returned something that is not the expected JSON", e);
        }
    }

    private static boolean isAnswer(Generation g) {
        if (g.getOutput().getText() == null || g.getOutput().getText().isBlank()) {
            return false;
        }
        Map<String, Object> props = g.getOutput().getMetadata();
        return !(props.containsKey("signature") || props.containsKey("data"));
    }

}
