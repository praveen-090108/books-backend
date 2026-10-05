package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long legacyRecordId;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", unique = true)
    private BusinessRecord resource;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_user_id")
    private AppUser manager;
    @Column(length = 160) private String name;
    @Column(unique = true, length = 160) private String email;
    @Column(length = 255) private String passwordHash;
    @Column(length = 32) private String phone;
    @Column(length = 120) private String designation;
    @Column(nullable = false, length = 160) private String roleName;
    @Column(nullable = false, length = 20) private String status = "Active";
    @Lob @Column(columnDefinition = "TEXT") private String moduleAccess;
    @Column(length = 64) private String resetTokenHash;
    private LocalDateTime resetTokenExpiresAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    @PrePersist void createDates() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void updateDate() { updatedAt = LocalDateTime.now(); }

    public String displayName() {
        return resource != null ? resource.getPartyName() : name;
    }

    public String loginEmail() {
        return resource != null ? resource.getPartyEmail() : email;
    }
}
