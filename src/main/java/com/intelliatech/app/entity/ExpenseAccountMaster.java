package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter @Setter @Entity
@Table(name = "expense_account_master", uniqueConstraints = @UniqueConstraint(name = "uk_expense_account_org_name", columnNames = {"organization_id", "account_name"}))
@EntityListeners(AuditingEntityListener.class)
public class ExpenseAccountMaster {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @Column(nullable = false, length = 120) private String accountName;
    @Column(length = 500) private String description;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private Integer displayOrder = 0;
    @Column(nullable = false, length = 120) private String createdBy = "System";
    @Column(nullable = false, length = 120) private String updatedBy = "System";
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false) private LocalDateTime updatedAt;
}
