package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "asset_depreciation_entries")
public class AssetDepreciationEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "schedule_id") private AssetDepreciationSchedule schedule;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asset_id") private Asset asset;
    @Column(nullable = false, length = 16) private String financialYear;
    @Column(nullable = false) private Integer periodNumber;
    @Column(nullable = false, length = 40) private String periodLabel;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private LocalDate endDate;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal openingBookValue;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal depreciationAmount;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal accumulatedDepreciation;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal closingBookValue;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AssetEnums.DepreciationEntryStatus status = AssetEnums.DepreciationEntryStatus.PENDING;
    private LocalDateTime postedAt;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
