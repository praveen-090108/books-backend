package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter @Setter @Entity
@Table(name="fixed_cost_project_milestone",uniqueConstraints=@UniqueConstraint(name="uk_project_milestone_name",columnNames={"project_id","milestone_name"}))
@EntityListeners(AuditingEntityListener.class)
public class FixedCostProjectMilestone {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id",nullable=false) private BusinessRecord project;
 @Column(name="milestone_name",nullable=false,length=180) private String name;
 @Column(length=1000) private String description;
 private LocalDate startDate;
 @Column(nullable=false) private LocalDate dueDate;
 @Column(nullable=false,precision=16,scale=2) private BigDecimal amount=BigDecimal.ZERO;
 @Column(nullable=false,precision=7,scale=2) private BigDecimal weightage;
 @Column(nullable=false,length=32) private String status="PLANNED";
 @Column(nullable=false,precision=7,scale=2) private BigDecimal progress=BigDecimal.ZERO;
 @Column(length=2000) private String notes;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="invoice_id") private BusinessRecord invoice;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_by",updatable=false) private Long createdBy;
 @Column(name="updated_by") private Long updatedBy;
 @CreatedDate @Column(nullable=false,updatable=false) private LocalDateTime createdAt;
 @LastModifiedDate @Column(nullable=false) private LocalDateTime updatedAt;
}
