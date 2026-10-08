package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="irp_configuration_audit")
public class IrpConfigurationAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long configurationId;
    private Long companyId;
    private String provider;
    @Enumerated(EnumType.STRING) private IrpEnvironment environment;
    private String action;
    private String performedBy;
    private LocalDateTime performedAt;
    @Column(length=1000) private String details;
}
