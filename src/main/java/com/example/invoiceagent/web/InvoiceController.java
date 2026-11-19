package com.example.invoiceagent.web;

import com.example.invoiceagent.model.service.InvoiceService;
import com.example.invoiceagent.model.dto.Status;
import com.example.invoiceagent.model.entity.InvoiceDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.MediaType.APPLICATION_XML;

@Controller
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService service;

    @GetMapping("/")
    public String index(Model model) {
        List<InvoiceDocument> all = service.all();
        model.addAttribute("invoices", all);
        model.addAttribute("extractor", service.extractorLabel());
        model.addAttribute("reviewCount", all.stream().filter(d -> d.getStatus() == Status.NEEDS_REVIEW).count());
        return "index";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("files") List<MultipartFile> files) throws IOException {
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                service.process(file.getOriginalFilename(), file.getBytes());
            }
        }
        return "redirect:/";
    }

    @GetMapping("/invoices/{id}")
    public String detail(@PathVariable long id, Model model) {
        InvoiceDocument doc = service.get(id);
        model.addAttribute("doc", doc);
        return "invoice";
    }

    @PostMapping("/invoices/{id}/approve")
    public String approve(@PathVariable long id) {
        service.approve(id);
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/invoices/{id}/reject")
    public String reject(@PathVariable long id) {
        service.reject(id);
        return "redirect:/invoices/" + id;
    }

    @GetMapping("/invoices/{id}/xrechnung.xml")
    public ResponseEntity<String> xml(@PathVariable long id) {
        InvoiceDocument doc = service.get(id);
        if (doc.getXrechnungXml() == null) {
            return ResponseEntity.notFound().build();
        }
        String name = (doc.getInvoiceNumber() == null ? "invoice-" + id : doc.getInvoiceNumber())
                .replaceAll("[^A-Za-z0-9._-]", "_") + "_xrechnung.xml";
        return ResponseEntity.ok()
                .contentType(APPLICATION_XML)
                .header(CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .body(doc.getXrechnungXml());
    }
}
