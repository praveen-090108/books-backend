package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter @Setter @Entity
@Table(name="domain_industry_master", uniqueConstraints=@UniqueConstraint(name="uk_domain_industry_org_name", columnNames={"organization_id","name"}))
@EntityListeners(AuditingEntityListener.class)
public class DomainIndustryMaster {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="organization_id", nullable=false) private Long organizationId=1L;
    @Column(nullable=false, length=160) private String name;
    @Column(length=500) private String description;
    @Column(nullable=false) private boolean active=true;
    @Column(name="display_order", nullable=false) private int displayOrder;
    @Column(name="created_by", updatable=false) private Long createdBy;
    @Column(name="updated_by") private Long updatedBy;
    @CreatedDate @Column(nullable=false, updatable=false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable=false) private LocalDateTime updatedAt;
}
