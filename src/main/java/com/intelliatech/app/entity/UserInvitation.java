package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name = "user_invitations")
public class UserInvitation {
    public enum Status { PENDING, ACCEPTED, CANCELLED, EXPIRED }
    public enum DeliveryStatus { QUEUED, SENT, FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "resource_id") private BusinessRecord resource;
    @Column(nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)") private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private DeliveryStatus deliveryStatus;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private LocalDateTime sentAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime revokedAt;
    @Column(length = 500) private String lastDeliveryError;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @PrePersist void createDates(){createdAt=updatedAt=LocalDateTime.now();}
    @PreUpdate void updateDate(){updatedAt=LocalDateTime.now();}
}
