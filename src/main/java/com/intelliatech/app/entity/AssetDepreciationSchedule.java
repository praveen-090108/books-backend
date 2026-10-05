package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "asset_depreciation_schedules")
public class AssetDepreciationSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asset_id") private Asset asset;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AssetEnums.DepreciationMethod method;
    @Column(nullable = false, length = 16) private String financialYear;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AssetEnums.Frequency frequency;
    @Column(nullable = false) private Integer numberOfPeriods;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AssetEnums.ResidualValueType residualValueType;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal residualValue;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal depreciableAmount;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal depreciationPerPeriod;
    @Column(name = "depreciation_expense_account", length = 120) private String expenseAccount;
    @Column(name = "accumulated_depreciation_account", length = 120) private String accumulatedAccount;
    @Column(length = 40) private String proRataConvention;
    @Column(nullable = false) private boolean depreciateInPurchaseMonth;
    @Column(nullable = false) private boolean includeInRun = true;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AssetEnums.DepreciationStatus status = AssetEnums.DepreciationStatus.DRAFT;
    private LocalDateTime lastRunAt;
    private LocalDate nextRunDate;
    @Column(nullable = false) private boolean deleted;
    @Column(nullable = false, length = 120) private String createdBy = "Admin";
    @Column(nullable = false, length = 120) private String updatedBy = "Admin";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Version private Long version;
    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
