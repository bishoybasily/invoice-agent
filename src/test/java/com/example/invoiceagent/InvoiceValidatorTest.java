package com.example.invoiceagent;

import com.example.invoiceagent.config.OpsProps;
import com.example.invoiceagent.ops.extraction.GroundTruthReplayExtractor;
import com.example.invoiceagent.model.dto.ExtractedInvoice;
import com.example.invoiceagent.model.dto.Finding;
import com.example.invoiceagent.ops.validation.InvoiceValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceValidatorTest {

    private final InvoiceValidator validator = new InvoiceValidator(new OpsProps());

    @TestFactory
    List<DynamicTest> rulesMatchGroundTruth() throws Exception {
        JsonNode all = new ObjectMapper().readTree(Files.readString(Samples.file("ground_truth.json")));
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode sample : all) {
            tests.add(DynamicTest.dynamicTest(sample.path("file").asText(), () -> {
                ExtractedInvoice inv = GroundTruthReplayExtractor.fromGroundTruth(sample);
                Set<String> errors = validator.validate(inv).stream()
                        .filter(Finding::isError).map(Finding::message).collect(Collectors.toSet());
                Set<String> expected = StreamSupport.stream(sample.path("expected_validation_errors").spliterator(), false)
                        .map(JsonNode::asText).collect(Collectors.toSet());
                assertThat(errors).isEqualTo(expected);
            }));
        }
        return tests;
    }

    @Test
    void zeroVatWithoutReverseChargeNoteIsAnError() throws Exception {
        JsonNode all = new ObjectMapper().readTree(Files.readString(Samples.file("ground_truth.json")));
        ExtractedInvoice rc = GroundTruthReplayExtractor.fromGroundTruth(all.get(2));
        ExtractedInvoice noNote = new ExtractedInvoice(rc.seller(), rc.buyer(), rc.invoiceNumber(), rc.invoiceDate(),
                rc.dueDate(), rc.orderReference(), rc.buyerReference(), rc.currency(), rc.lines(), rc.vatBreakdown(),
                rc.netTotal(), rc.vatTotal(), rc.grossTotal(), "", 1.0, List.of());
        assertThat(validator.validate(noNote)).extracting(Finding::code).contains("BR-AE-10");
    }

    @Test
    void lineArithmeticIsChecked() throws Exception {
        JsonNode all = new ObjectMapper().readTree(Files.readString(Samples.file("ground_truth.json")));
        ExtractedInvoice inv = GroundTruthReplayExtractor.fromGroundTruth(all.get(0));
        var lines = new ArrayList<>(inv.lines());
        var first = lines.get(0);
        lines.set(0, new ExtractedInvoice.Line(first.description(), "5", first.unitPrice(), first.netAmount(), first.vatRate()));
        ExtractedInvoice broken = new ExtractedInvoice(inv.seller(), inv.buyer(), inv.invoiceNumber(), inv.invoiceDate(),
                inv.dueDate(), inv.orderReference(), inv.buyerReference(), inv.currency(), lines, inv.vatBreakdown(),
                inv.netTotal(), inv.vatTotal(), inv.grossTotal(), inv.note(), 1.0, List.of());
        assertThat(validator.validate(broken)).extracting(Finding::code).contains("LINE-AMOUNT");
    }

    @Test
    void lowConfidenceNeedsReviewButIsNoError() throws Exception {
        JsonNode all = new ObjectMapper().readTree(Files.readString(Samples.file("ground_truth.json")));
        ExtractedInvoice inv = GroundTruthReplayExtractor.fromGroundTruth(all.get(0));
        ExtractedInvoice unsure = new ExtractedInvoice(inv.seller(), inv.buyer(), inv.invoiceNumber(), inv.invoiceDate(),
                inv.dueDate(), inv.orderReference(), inv.buyerReference(), inv.currency(), inv.lines(), inv.vatBreakdown(),
                inv.netTotal(), inv.vatTotal(), inv.grossTotal(), inv.note(), 0.99, List.of("iban"));
        assertThat(validator.validate(inv)).noneMatch(Finding::needsReview);
        assertThat(validator.validate(unsure)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo("LOW-CONFIDENCE");
            assertThat(f.isError()).isFalse();
            assertThat(f.message()).contains("unsure about: iban");
        });
    }
}
