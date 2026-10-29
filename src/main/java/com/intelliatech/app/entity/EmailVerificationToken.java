package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "email_verification_tokens")
public class EmailVerificationToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @Column(nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)") private String tokenHash;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void createDate() { createdAt = LocalDateTime.now(); }
}
