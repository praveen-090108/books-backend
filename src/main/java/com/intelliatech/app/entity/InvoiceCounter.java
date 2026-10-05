package com.intelliatech.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "invoice_counters")
public class InvoiceCounter {

    @Id
    @Column(name = "counter_key", length = 64)
    private String key;

    @Column(nullable = false)
    private Long nextValue;
}
