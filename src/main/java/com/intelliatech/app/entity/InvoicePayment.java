package com.intelliatech.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Table(name = "invoice_payments")
@EntityListeners(AuditingEntityListener.class)
public class InvoicePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_invoice_id", nullable = false)
    private BusinessRecord primaryInvoice;

    private Long customerId;

    @Column(nullable = false, unique = true, length = 64)
    private String paymentNumber;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 160)
    private String customerName;

    @Column(nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(length = 40)
    private String paymentMode;

    @Column(length = 160)
    private String depositAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private BankAccountMaster bankAccount;

    @Column(length = 160)
    private String bankAccountName;

    @Column(length = 160)
    private String bankName;

    @Column(length = 32)
    private String maskedAccountNumber;

    @Column(length = 100)
    private String referenceNumber;

    @Column(length = 100)
    private String transactionId;

    @Column(length = 80)
    private String chequeNumber;

    private LocalDate chequeDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmountReceived = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal bankCharges = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netBankCredit = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 1024)
    private String attachmentUrl;

    @Column(nullable = false)
    private Boolean sendThankYouEmail = false;

    @Column(nullable = false)
    private Boolean reconciled = false;

    @Column(nullable = false)
    private Boolean reversed = false;

    private LocalDateTime reversedAt;

    @Column(length = 500)
    private String reversalReason;

    @Column(length = 120)
    private String createdBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
