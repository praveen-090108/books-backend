package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "asset_maintenance")
public class AssetMaintenance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @Column(nullable = false, length = 64) private String workOrderNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asset_id") private Asset asset;
    @Column(nullable = false, length = 80) private String maintenanceType;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String checklistJson;
    private Long technicianResourceId;
    @Column(name = "technician_name", length = 180) private String technicianResourceName;
    private Long assistantResourceId;
    @Column(name = "assistant_name", length = 180) private String assistantResourceName;
    @Column(nullable = false) private LocalDate scheduledDate;
    @Column(nullable = false) private LocalDate dueDate;
    private LocalDate completedDate;
    @Column(precision = 10, scale = 2) private BigDecimal estimatedDurationHours = BigDecimal.ZERO;
    @Column(precision = 10, scale = 2) private BigDecimal actualDurationHours = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal estimatedCost = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal actualCost = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal partsCost = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal labourCost = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(length = 24) private AssetEnums.Frequency repeatFrequency = AssetEnums.Frequency.NONE;
    private LocalDate nextDueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AssetEnums.MaintenancePriority priority = AssetEnums.MaintenancePriority.MEDIUM;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AssetEnums.MaintenanceStatus status = AssetEnums.MaintenanceStatus.DRAFT;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(length = 255) private String attachmentName;
    @Column(length = 1000) private String attachmentUrl;
    @Column(nullable = false) private boolean deleted;
    @Column(nullable = false, length = 120) private String createdBy = "Admin";
    @Column(nullable = false, length = 120) private String updatedBy = "Admin";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Version private Long version;
    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
