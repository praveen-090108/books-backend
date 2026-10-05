package com.intelliatech.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_id", nullable = false) private PurchaseOrder purchaseOrder;
    private Long itemId;
    @Column(nullable = false, length = 180) private String itemName;
    @Column(length = 100) private String itemSku;
    @Column(length = 24) private String itemType;
    @Column(length = 1000) private String description;
    @Column(length = 120) private String accountName;
    @Column(length = 24) private String hsnCode;
    @Column(length = 24) private String sacCode;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal quantity;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal receivedQuantity = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal billedQuantity = BigDecimal.ZERO;
    @Column(length = 40) private String unit;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal rate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private PurchaseOrderDiscountType discountType = PurchaseOrderDiscountType.NONE;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal discountValue = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tax_id") private TaxRate tax;
    @Column(length = 120) private String taxName;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal taxRate = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal taxableAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal cgstAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal sgstAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal igstAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal cessAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal lineTotal = BigDecimal.ZERO;
    @Column(length = 160) private String warehouseName;
    @Column(length = 160) private String projectName;
    @Column(nullable = false) private int sortOrder;
}
