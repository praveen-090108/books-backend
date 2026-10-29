package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "plans")
public class Plan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal price;
    @Column(nullable = false, length = 24) private String billingCycle;
    @Column(nullable = false) private boolean active;
    private Integer userLimit;
    private Long storageLimitBytes;
    @Lob @Column(columnDefinition = "TEXT") private String enabledFeaturesJson;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
}
