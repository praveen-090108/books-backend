package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "asset_disposals")
public class AssetDisposal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @Column(nullable = false, length = 64) private String disposalNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asset_id") private Asset asset;
    @Column(nullable = false) private LocalDate disposalDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AssetEnums.DisposalMethod disposalMethod;
    private Long buyerVendorId;
    @Column(length = 180) private String buyerVendorName;
    @Column(length = 120) private String referenceNumber;
    @Column(nullable = false, length = 500) private String reason;
    @Column(length = 120) private String gainLossAccount;
    @Enumerated(EnumType.STRING) @Column(length = 40) private AssetEnums.DepreciationMethod depreciationMethod;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal accumulatedDepreciation;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal netBookValue;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal disposalValue;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal gainLossAmount;
    @Column(nullable = false, precision = 12, scale = 4) private BigDecimal gainLossPercentage;
    @Column(columnDefinition = "TEXT") private String remarks;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AssetEnums.DisposalStatus status = AssetEnums.DisposalStatus.DRAFT;
    @Column(length = 120) private String approvedBy;
    private LocalDateTime approvedAt;
    @Column(length = 500) private String approvalNotes;
    private LocalDateTime completedAt;
    private LocalDateTime reversedAt;
    @Column(nullable = false) private boolean deleted;
    @Column(nullable = false, length = 120) private String createdBy = "Admin";
    @Column(nullable = false, length = 120) private String updatedBy = "Admin";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Version private Long version;
    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
