package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter @Setter @Entity @Table(name = "asset_categories")
@EntityListeners(AuditingEntityListener.class)
public class AssetCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @Column(nullable = false, length = 24) private String categoryCode;
    @Column(nullable = false, length = 120) private String categoryName;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AssetEnums.DepreciationMethod defaultDepreciationMethod;
    @Column(nullable = false) private Integer usefulLifeYears;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal residualValuePercentage = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AssetEnums.Frequency depreciationFrequency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AssetEnums.AssetCondition defaultAssetCondition;
    @Column(length = 16) private String displayColor = "#ef233c";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AssetEnums.CategoryStatus status = AssetEnums.CategoryStatus.ACTIVE;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(nullable = false) private boolean deleted;
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false) private LocalDateTime updatedAt;
    @Version private Long version;
}
