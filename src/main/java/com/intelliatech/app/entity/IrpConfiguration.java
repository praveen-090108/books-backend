package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name = "irp_configuration", uniqueConstraints = @UniqueConstraint(name = "uk_irp_configuration_company_provider_env", columnNames = {"company_id", "provider", "environment"}))
public class IrpConfiguration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="company_id", nullable=false) private Long companyId = 1L;
    @Column(nullable=false, length=40) private String provider = "EY_IRP_5";
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private IrpEnvironment environment;
    @Column(name="api_base_url", nullable=false, length=500) private String apiBaseUrl;
    @Column(name="client_id_encrypted", nullable=false, columnDefinition="TEXT") private String clientIdEncrypted;
    @Column(name="client_secret_encrypted", nullable=false, columnDefinition="TEXT") private String clientSecretEncrypted;
    @Column(name="api_username_encrypted", nullable=false, columnDefinition="TEXT") private String apiUsernameEncrypted;
    @Column(name="api_password_encrypted", nullable=false, columnDefinition="TEXT") private String apiPasswordEncrypted;
    @Column(nullable=false, length=15) private String gstin;
    @Column(name="api_version", length=20) private String apiVersion;
    @Column(name="is_active", nullable=false) private boolean active;
    @Column(name="is_configured", nullable=false) private boolean configured = true;
    @Column(name="credential_version", nullable=false) private Long credentialVersion = 1L;
    @Column(name="last_connection_test") private LocalDateTime lastConnectionTest;
    @Column(name="last_connection_status", length=20) private String lastConnectionStatus;
    @Column(name="created_by") private String createdBy;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_by") private String updatedBy;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @Version private Long version;

    @PrePersist void create() { var now=LocalDateTime.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void update() { updatedAt=LocalDateTime.now(); }
}
