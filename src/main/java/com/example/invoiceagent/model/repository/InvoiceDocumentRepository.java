package com.example.invoiceagent.model.repository;

import com.example.invoiceagent.model.entity.InvoiceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceDocumentRepository extends JpaRepository<InvoiceDocument, Long> {

    List<InvoiceDocument> findAllByOrderByIdDesc();

}
