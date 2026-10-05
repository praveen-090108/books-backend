package com.intelliatech.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Table(name = "vendor_bank_details")
@EntityListeners(AuditingEntityListener.class)
public class VendorBankDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false, unique = true)
    private Vendor vendor;

    @Column(length = 160)
    private String accountHolderName;

    @Column(length = 160)
    private String beneficiaryName;

    @Column(length = 160)
    private String bankName;

    @Column(length = 80)
    private String accountNumber;

    @Column(length = 16)
    private String ifscCode;

    @Column(length = 160)
    private String branchName;

    @Column(length = 24)
    private String accountType;

    @Column(length = 16)
    private String swiftCode;

    @Column(length = 40)
    private String iban;

    @Column(length = 120)
    private String bankCountry;

    @Column(length = 500)
    private String bankAddress;

    @Column(length = 160)
    private String upiId;

    @Column(length = 1000)
    private String notes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
