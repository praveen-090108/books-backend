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
@Table(name = "supply_states")
public class SupplyState {
    @Id
    @Column(length = 2)
    private String code;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 32)
    private String territoryType;
}
