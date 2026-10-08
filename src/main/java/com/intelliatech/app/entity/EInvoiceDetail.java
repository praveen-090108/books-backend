package com.intelliatech.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Table(name = "e_invoice_details")
@EntityListeners(AuditingEntityListener.class)
public class EInvoiceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", unique = true)
    private BusinessRecord invoice;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_note_id", unique = true)
    private BusinessRecord creditNote;

    @Column(name = "irp_provider", length = 40)
    private String irpProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "irp_environment", length = 20)
    private IrpEnvironment irpEnvironment;

    @Column(name = "irp_gstin", length = 15)
    private String irpGstin;

    @Column(length = 128, unique = true)
    private String irn;

    @Column(name = "ack_no", length = 64)
    private String acknowledgementNumber;

    @Column(name = "ack_date")
    private LocalDateTime acknowledgementDate;

    @Lob
    @Column(name = "signed_invoice", columnDefinition = "LONGTEXT")
    private String signedInvoice;

    @Lob
    @Column(name = "signed_qr_code", columnDefinition = "LONGTEXT")
    private String signedQrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EInvoiceStatus status = EInvoiceStatus.NOT_GENERATED;

    @Lob
    @Column(name = "irp_request", columnDefinition = "LONGTEXT")
    private String irpRequest;

    @Lob
    @Column(name = "irp_response", columnDefinition = "LONGTEXT")
    private String irpResponse;

    private LocalDateTime generatedAt;
    private LocalDateTime cancelledAt;

    @Column(length = 8)
    private String cancelReason;

    @Column(length = 500)
    private String cancelRemarks;

    @Column(name = "eway_bill_no", length = 32)
    private String ewayBillNumber;

    @Column(name = "eway_bill_date")
    private LocalDateTime ewayBillDate;

    @Column(name = "eway_bill_valid_till")
    private LocalDateTime ewayBillValidTill;

    @Column(length = 1000)
    private String remarks;

    @Column(length = 64)
    private String errorCode;

    @Column(length = 2000)
    private String errorMessage;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
