package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter @Setter @Entity
@Table(name = "bank_account_master", uniqueConstraints = @UniqueConstraint(name = "uk_bank_account_org_name", columnNames = {"organization_id", "account_name"}))
@EntityListeners(AuditingEntityListener.class)
public class BankAccountMaster {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @Column(nullable = false, length = 160) private String accountName;
    @Column(nullable = false, length = 160) private String bankName;
    @Column(length = 160) private String accountHolderName;
    @Column(length = 128) private String accountNumber;
    @Column(length = 40) private String accountType;
    @Column(length = 32) private String ifscCode;
    @Column(length = 160) private String branchName;
    @Column(nullable = false, length = 3) private String currencyCode = "INR";
    @Column(precision = 19, scale = 2) private BigDecimal openingBalance;
    @Column(nullable = false) private boolean active = true;
    @Column(length = 1000) private String notes;
    @Column(nullable = false, length = 120) private String createdBy = "System";
    @Column(nullable = false, length = 120) private String updatedBy = "System";
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false) private LocalDateTime updatedAt;
}
