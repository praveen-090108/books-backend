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
@Table(name = "expenses")
@EntityListeners(AuditingEntityListener.class)
public class Expense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @Column(nullable = false, length = 64) private String expenseNumber;
    @Column(nullable = false) private LocalDate expenseDate;
    @Column(nullable = false, length = 120) private String expenseAccount;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "expense_account_id") private ExpenseAccountMaster expenseAccountMaster;
    @Column(nullable = false, length = 180) private String expenseTitle;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ExpenseType expenseType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vendor_id") private Vendor vendor;
    @Column(length = 100) private String invoiceNumber;
    @Column(length = 16) private String hsnCode;
    @Column(length = 16) private String sacCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 48) private GstTreatment gstTreatment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_of_supply") private SupplyState sourceOfSupply;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "destination_of_supply") private SupplyState destinationOfSupply;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tax_id") private TaxRate tax;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal taxRate = BigDecimal.ZERO;
    @Column(length = 120) private String taxName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ExpenseAmountType amountType;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal enteredAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal taxableAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal cgstAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal sgstAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal igstAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal cessAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal totalTaxAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal tdsDeducted = BigDecimal.ZERO;
    @Column(nullable = false, length = 64) private String currency = "INR - Indian Rupee";
    @Column(length = 100) private String referenceNumber;
    @Column(length = 1000) private String description;
    @Column(length = 1000) private String notes;
    @Column(length = 64) private String paymentMode;
    @Column(length = 120) private String paidThrough;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "bank_account_id") private BankAccountMaster bankAccount;
    @Column(length = 160) private String projectName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ExpenseStatus status = ExpenseStatus.PAID;
    @Column(length = 255) private String attachmentName;
    @Column(length = 1000) private String attachmentUrl;
    @Column(nullable = false, length = 120) private String createdBy = "Admin";
    @Column(nullable = false) private boolean deleted;
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false) private LocalDateTime updatedAt;
}
