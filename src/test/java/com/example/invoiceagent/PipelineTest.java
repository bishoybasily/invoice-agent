package com.example.invoiceagent;

import com.example.invoiceagent.model.dto.Status;
import com.example.invoiceagent.model.entity.InvoiceDocument;
import com.example.invoiceagent.model.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PipelineTest {

    @Autowired
    InvoiceService service;

    @Test
    void samplesAreRoutedCorrectly() throws Exception {
        Map<String, Status> expected = new LinkedHashMap<>();
        expected.put("01_clean_de_single_vat.pdf", Status.VALID);
        expected.put("02_clean_de_mixed_vat.pdf", Status.VALID);
        expected.put("03_clean_en_reverse_charge.pdf", Status.VALID);
        expected.put("04_error_wrong_total.pdf", Status.NEEDS_REVIEW);
        expected.put("05_error_missing_vat_id.pdf", Status.NEEDS_REVIEW);
        expected.put("06_scanned_copy_of_01.pdf", Status.NEEDS_REVIEW);

        for (var e : expected.entrySet()) {
            InvoiceDocument doc = service.process(e.getKey(), Files.readAllBytes(Path.of("samples", e.getKey())));
            assertThat(doc.getStatus()).as(e.getKey()).isEqualTo(e.getValue());
            if (doc.getStatus() == Status.VALID) {
                Document xml = DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder()
                        .parse(new ByteArrayInputStream(doc.getXrechnungXml().getBytes(StandardCharsets.UTF_8)));
                assertThat(xml.getDocumentElement().getLocalName()).isEqualTo("Invoice");
            }
        }
    }

    @Test
    void approvingAReviewItemProducesXRechnung() throws Exception {
        InvoiceDocument doc = service.process("06_scanned_copy_of_01.pdf",
                Files.readAllBytes(Path.of("samples/06_scanned_copy_of_01.pdf")));
        assertThat(doc.getXrechnungXml()).isNull();
        InvoiceDocument approved = service.approve(doc.getId());
        assertThat(approved.getStatus()).isEqualTo(Status.APPROVED);
        assertThat(approved.getXrechnungXml()).contains("<cbc:PayableAmount currencyID=\"EUR\">341.95</cbc:PayableAmount>");
    }

    @Test
    void invoiceWithRuleErrorCannotBeApproved() throws Exception {
        InvoiceDocument doc = service.process("04_error_wrong_total.pdf",
                Files.readAllBytes(Path.of("samples/04_error_wrong_total.pdf")));
        assertThatThrownBy(() -> service.approve(doc.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(service.get(doc.getId()).getStatus()).isEqualTo(Status.NEEDS_REVIEW);
    }
}
