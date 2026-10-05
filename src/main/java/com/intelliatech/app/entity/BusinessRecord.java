package com.intelliatech.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Table(name = "business_records")
@EntityListeners(AuditingEntityListener.class)
public class BusinessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 32)
    private String module;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String type;

    @NotBlank
    @Column(nullable = false, unique = true, length = 64)
    private String recordNumber;

    @NotBlank
    @Column(nullable = false, length = 160)
    private String partyName;

    @Column(length = 160)
    private String partyEmail;

    @Column(length = 32)
    private String partyPhone;

    @Column(length = 120)
    private String partyCity;

    @Column(length = 80)
    private String category;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String status;

    @Column(length = 64)
    private String secondaryStatus;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @PositiveOrZero
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAmount = BigDecimal.ZERO;

    @NotNull
    @Column(nullable = false)
    private LocalDate recordDate;

    private LocalDate dueDate;

    private LocalDate closedDate;

    @Column(length = 64)
    private String referenceNumber;

    @Column(length = 64)
    private String paymentMode;

    @Column(length = 120)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Department department;

    @Column(length = 150)
    private String designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private BusinessRecord reportingManager;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
