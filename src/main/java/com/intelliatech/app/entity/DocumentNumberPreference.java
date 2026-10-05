package com.intelliatech.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Table(name = "document_number_preferences")
@EntityListeners(AuditingEntityListener.class)
public class DocumentNumberPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 64)
    private String documentType;

    @NotNull
    @Column(nullable = false)
    private Boolean autoGenerate = true;

    @Column(length = 32)
    private String prefix = "";

    @Column(length = 32)
    private String suffix = "";

    @Column(name = "number_separator", length = 8)
    private String separator = "-";

    @NotBlank
    @Column(nullable = false, length = 32)
    private String numberFormat = "000";

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Long startingNumber = 1L;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Long nextNumber = 1L;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
