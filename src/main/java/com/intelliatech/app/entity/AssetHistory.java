package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "asset_history")
public class AssetHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asset_id") private Asset asset;
    @Column(nullable = false, length = 64) private String eventType;
    @Column(length = 32) private String fromStatus;
    @Column(length = 32) private String toStatus;
    @Column(length = 48) private String referenceType;
    private Long referenceId;
    @Column(nullable = false, length = 1000) private String description;
    @Column(nullable = false, length = 120) private String actor = "Admin";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
