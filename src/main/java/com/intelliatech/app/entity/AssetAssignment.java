package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "asset_assignments")
public class AssetAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asset_id") private Asset asset;
    @Column(nullable = false) private Long resourceId;
    @Column(nullable = false, length = 180) private String resourceName;
    @Column(length = 180) private String resourceEmail;
    @Column(nullable = false) private LocalDate assignmentDate;
    private LocalDate expectedReturnDate;
    private LocalDate actualReturnDate;
    @Column(length = 500) private String purpose;
    @Column(length = 120) private String costCenter;
    @Column(length = 160) private String projectName;
    @Column(length = 120) private String referenceNumber;
    @Column(columnDefinition = "TEXT") private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AssetEnums.AssignmentStatus status = AssetEnums.AssignmentStatus.DRAFT;
    @Column(nullable = false, length = 120) private String createdBy = "Admin";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Version private Long version;
    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
