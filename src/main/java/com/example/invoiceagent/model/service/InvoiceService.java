package com.example.invoiceagent.model.service;

import com.example.invoiceagent.ops.extraction.InvoiceExtractor;
import com.example.invoiceagent.model.dto.ExtractedInvoice;
import com.example.invoiceagent.model.dto.ExtractionResult;
import com.example.invoiceagent.model.dto.Status;
import com.example.invoiceagent.model.entity.InvoiceDocument;
import com.example.invoiceagent.model.repository.InvoiceDocumentRepository;
import com.example.invoiceagent.ops.validation.InvoiceValidator;
import com.example.invoiceagent.ops.xrechnung.XRechnungWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static com.example.invoiceagent.model.dto.Status.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceExtractor extractor;
    private final InvoiceValidator validator;
    private final XRechnungWriter xRechnung;
    private final InvoiceDocumentRepository repository;
    private final TransactionTemplate tx;

    public String extractorLabel() {
        return extractor.getLabel();
    }

    public InvoiceDocument process(String fileName, byte[] pdf) {
        InvoiceDocument doc = new InvoiceDocument(fileName);
        long start = System.currentTimeMillis();
        try {
            ExtractedInvoice invoice = extractor.extract(fileName, pdf);
            ExtractionResult result = new ExtractionResult(extractor.getLabel(), invoice, validator.validate(invoice));
            doc.setResult(result);
            doc.setInvoiceNumber(invoice.invoiceNumber());
            doc.setSellerName(invoice.seller() == null ? "" : invoice.seller().name());
            doc.setGrossTotal(invoice.grossTotal() + " " + invoice.currency());
            if (!result.needsReview()) {
                doc.setXrechnungXml(xRechnung.write(invoice));
                doc.setStatus(VALID);
            } else {
                doc.setStatus(NEEDS_REVIEW);
            }
        } catch (Exception e) {
            log.warn("Processing {} failed", fileName, e);
            doc.setStatus(FAILED);
            doc.setError(truncate(e.getMessage(), 2000));
        }
        doc.setDurationMs(System.currentTimeMillis() - start);
        return tx.execute(status -> repository.save(doc));
    }

    public InvoiceDocument approve(long id) {
        return tx.execute(status -> {
            InvoiceDocument doc = load(id);
            ExtractionResult result = doc.getResult();
            if (result == null || result.hasErrors()) {
                throw new IllegalStateException("Invoice " + id + " has validation errors and cannot be approved");
            }
            if (doc.getXrechnungXml() == null) {
                doc.setXrechnungXml(xRechnung.write(result.invoice()));
            }
            doc.setStatus(Status.APPROVED);
            doc.setDecidedAt(Instant.now());
            return repository.save(doc);
        });
    }

    public void reject(long id) {
        tx.executeWithoutResult(status -> {
            InvoiceDocument doc = load(id);
            doc.setStatus(Status.REJECTED);
            doc.setDecidedAt(Instant.now());
            repository.save(doc);
        });
    }

    public InvoiceDocument get(long id) {
        return load(id);
    }

    public List<InvoiceDocument> all() {
        return repository.findAllByOrderByIdDesc();
    }

    private InvoiceDocument load(long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("No invoice " + id));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "unknown error";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
