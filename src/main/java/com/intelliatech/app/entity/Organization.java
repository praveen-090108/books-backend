package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name = "organizations")
public class Organization {
    public enum Status { PENDING_VERIFICATION, ACTIVE, SUSPENDED, DEACTIVATED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 36, columnDefinition = "CHAR(36)") private String publicId;
    @Column(nullable = false, unique = true, length = 100) private String slug;
    @Column(nullable = false, length = 180) private String organizationName;
    @Column(length = 180) private String legalName;
    @Column(unique = true, length = 15) private String gstin;
    @Column(length = 10) private String pan;
    @Column(nullable = false, length = 2, columnDefinition = "CHAR(2)") private String countryCode = "IN";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Status status;
    @Column(nullable = false) private long securityVersion;
    private LocalDateTime verifiedAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    @PrePersist void createDates() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void updateDate() { updatedAt = LocalDateTime.now(); }
}
