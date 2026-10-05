package com.intelliatech.app.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "vendors")
@EntityListeners(AuditingEntityListener.class)
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizationId = 1L;

    @Column(nullable = false, length = 64)
    private String vendorNumber;

    @Column(nullable = false, length = 160)
    private String vendorName;

    @Column(nullable = false, length = 160)
    private String displayName;

    @Column(length = 160)
    private String companyName;

    @Column(length = 64)
    private String vendorType;

    @Column(length = 120)
    private String sourceOfSupply;

    @Column(nullable = false, length = 64)
    private String currency = "INR - Indian Rupee";

    @Column(length = 64)
    private String paymentTerms;

    @Column(length = 80)
    private String taxTreatment;

    @Column(length = 20)
    private String gstin;

    @Column(length = 16)
    private String pan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VendorStatus status = VendorStatus.ACTIVE;

    @Column(length = 160)
    private String primaryContact;

    @Column(length = 180)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 40)
    private String mobile;

    @Column(length = 255)
    private String website;

    @Column(length = 255)
    private String billingAddressLine1;

    @Column(length = 255)
    private String billingAddressLine2;

    @Column(length = 120)
    private String billingCity;

    @Column(length = 120)
    private String billingState;

    @Column(length = 20)
    private String billingPincode;

    @Column(length = 120)
    private String billingCountry;

    @Column(length = 255)
    private String shippingAddressLine1;

    @Column(length = 255)
    private String shippingAddressLine2;

    @Column(length = 120)
    private String shippingCity;

    @Column(length = 120)
    private String shippingState;

    @Column(length = 20)
    private String shippingPincode;

    @Column(length = 120)
    private String shippingCountry;

    @OneToOne(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private VendorBankDetails bankDetails;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void setBankDetails(VendorBankDetails details) {
        this.bankDetails = details;
        if (details != null) details.setVendor(this);
    }
}
