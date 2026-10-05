package com.intelliatech.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Table(name = "bill_activities")
@EntityListeners(AuditingEntityListener.class)
public class BillActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bill_id", nullable = false) private Bill bill;
    @Column(nullable = false, length = 64) private String action;
    @Column(length = 2000) private String details;
    @Column(nullable = false, length = 120) private String performedBy = "Admin";
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
}
