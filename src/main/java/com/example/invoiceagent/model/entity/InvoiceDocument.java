package com.example.invoiceagent.model.entity;

import com.example.invoiceagent.model.dto.ExtractionResult;
import com.example.invoiceagent.model.dto.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "invoice_document")
public class InvoiceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private String invoiceNumber;
    private String sellerName;
    private String grossTotal;
    private long durationMs;
    private Instant receivedAt = Instant.now();
    private Instant decidedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private ExtractionResult result;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String xrechnungXml;

    @Column(length = 2000)
    private String error;

    public InvoiceDocument(String fileName) {
        this.fileName = fileName;
    }
}
