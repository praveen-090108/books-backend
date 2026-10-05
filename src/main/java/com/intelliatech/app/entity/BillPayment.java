package com.intelliatech.app.entity;

import jakarta.persistence.*;
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
@Table(name = "bill_payments")
@EntityListeners(AuditingEntityListener.class)
public class BillPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bill_id", nullable = false) private Bill bill;
    @Column(length = 80, unique = true) private String idempotencyKey;
    @Column(nullable = false, length = 64, unique = true) private String paymentNumber;
    @Column(nullable = false) private LocalDate paymentDate;
    @Column(nullable = false, length = 64) private String paymentMode;
    @Column(nullable = false, length = 160) private String paidThrough;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "bank_account_id") private BankAccountMaster bankAccount;
    @Column(length = 160) private String toAccount;
    @Column(length = 120) private String referenceNumber;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(length = 2000) private String notes;
    @Column(length = 255) private String attachmentName;
    @Column(length = 1000) private String attachmentUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private BillPaymentStatus status = BillPaymentStatus.PAID;
    private LocalDateTime reversedAt;
    @Column(length = 120) private String reversedBy;
    @Column(length = 1000) private String reversalReason;
    @Column(nullable = false, length = 120) private String createdBy = "Admin";
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false) private LocalDateTime updatedAt;
}
