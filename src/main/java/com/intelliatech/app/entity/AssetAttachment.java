package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "asset_attachments")
public class AssetAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asset_id") private Asset asset;
    @Column(nullable = false, length = 255) private String fileName;
    @Column(nullable = false, length = 1000) private String fileUrl;
    @Column(length = 1000) private String storageKey;
    @Column(length = 120) private String contentType;
    private Long fileSize;
    @Column(length = 48) private String attachmentType;
    @Column(nullable = false) private boolean active = true;
    private Long uploadedBy;
    private Long deletedBy;
    private LocalDateTime deletedAt;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
