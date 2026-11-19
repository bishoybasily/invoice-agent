package com.example.invoiceagent.ops.extraction;

import com.example.invoiceagent.model.dto.ExtractedInvoice;
import com.example.invoiceagent.model.dto.ExtractedInvoice.Line;
import com.example.invoiceagent.model.dto.ExtractedInvoice.Party;
import com.example.invoiceagent.model.dto.ExtractedInvoice.VatSubtotal;
import com.example.invoiceagent.model.exception.ExtractionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroundTruthReplayExtractor implements InvoiceExtractor {

    private final Map<String, JsonNode> byFile = new HashMap<>();

    public GroundTruthReplayExtractor(Path groundTruthJson, ObjectMapper mapper) {
        try {
            for (JsonNode node : mapper.readTree(Files.readString(groundTruthJson))) {
                byFile.put(node.path("file").asText(), node);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + groundTruthJson, e);
        }
    }

    @Override
    public String getLabel() {
        return "Replay (ground_truth.json, no AI)";
    }

    @Override
    public ExtractedInvoice extract(String fileName, byte[] pdf) {
        JsonNode n = byFile.get(fileName);
        if (n == null) {
            throw new ExtractionException("Replay mode only knows the sample files; configure Claude or Gemini to read " + fileName);
        }
        return fromGroundTruth(n);
    }

    public static ExtractedInvoice fromGroundTruth(JsonNode n) {
        List<Line> lines = new ArrayList<>();
        n.path("lines").forEach(l -> lines.add(new Line(
                text(l, "description"), text(l, "quantity"), text(l, "unit_price"),
                text(l, "net_amount"), text(l, "vat_rate"))));
        List<VatSubtotal> vat = new ArrayList<>();
        n.path("vat_breakdown").forEach(v -> vat.add(new VatSubtotal(
                text(v, "vat_rate"), text(v, "taxable_amount"), text(v, "vat_amount"))));
        boolean scanned = n.path("file").asText().contains("scanned");
        return new ExtractedInvoice(
                party(n.path("seller")),
                party(n.path("buyer")),
                text(n, "invoice_number"),
                text(n, "invoice_date"),
                text(n, "due_date"),
                text(n, "order_reference"),
                text(n.path("buyer"), "buyer_reference"),
                text(n, "currency"),
                lines,
                vat,
                text(n, "net_total"),
                text(n, "vat_total"),
                text(n, "gross_total_printed"),
                scanned ? "" : text(n, "note"),
                scanned ? 0.6 : 1.0,
                scanned ? List.of("all fields (image-only scan)") : List.of());
    }

    private static Party party(JsonNode p) {
        return new Party(text(p, "name"), text(p, "street"), text(p, "city"), text(p, "vat_id"), text(p, "iban"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }
}
