package com.intelliatech.app.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Table(name = "purchase_orders")
@EntityListeners(AuditingEntityListener.class)
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @Column(nullable = false, length = 64) private String purchaseOrderNumber;
    @Column(nullable = false) private LocalDate purchaseOrderDate;
    private LocalDate expectedDeliveryDate;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vendor_id") private Vendor vendor;
    @Column(nullable = false, length = 160) private String vendorName;
    @Column(columnDefinition = "TEXT") private String vendorAddressJson;
    @Column(columnDefinition = "TEXT") private String deliveryAddressJson;
    @Column(length = 40) private String deliveryAddressSource;
    @Column(length = 100) private String referenceNumber;
    @Column(length = 80) private String shipmentPreference;
    @Column(length = 64) private String paymentTerms;
    @Column(nullable = false, length = 16) private String currencyCode = "INR";
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal exchangeRate = BigDecimal.ONE;
    @Enumerated(EnumType.STRING) @Column(length = 48) private GstTreatment gstTreatment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_of_supply") private SupplyState sourceOfSupply;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "destination_of_supply") private SupplyState destinationOfSupply;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "place_of_supply") private SupplyState placeOfSupply;
    @Column(length = 160) private String projectName;
    @Column(length = 160) private String branchName;
    @Column(length = 160) private String warehouseName;
    @Column(length = 160) private String attention;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ExpenseAmountType amountType = ExpenseAmountType.TAX_EXCLUSIVE;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal taxableAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal cgstAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal sgstAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal igstAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal cessAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal shippingCharge = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal adjustmentAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal roundOffAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal totalTaxAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(columnDefinition = "TEXT") private String termsAndConditions;
    @Column(length = 255) private String attachmentName;
    @Column(length = 1000) private String attachmentUrl;
    private Long linkedBillId;
    @Column(length = 64) private String linkedBillNumber;
    private LocalDateTime issuedAt;
    @Column(length = 120) private String issuedBy;
    private LocalDateTime receivedAt;
    @Column(length = 120) private String receivedBy;
    private LocalDateTime cancelledAt;
    @Column(length = 120) private String cancelledBy;
    @Column(length = 1000) private String cancellationReason;
    private LocalDateTime closedAt;
    @Column(length = 120) private String closedBy;
    @Column(length = 1000) private String closingReason;
    @Column(nullable = false, length = 120) private String createdBy = "Admin";
    @Column(nullable = false, length = 120) private String updatedBy = "Admin";
    @Column(nullable = false) private boolean deleted;
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false) private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt desc")
    private List<PurchaseOrderActivity> activities = new ArrayList<>();

    public void replaceItems(List<PurchaseOrderItem> replacements) {
        items.clear();
        replacements.forEach(item -> {
            item.setPurchaseOrder(this);
            items.add(item);
        });
    }

    public void addActivity(PurchaseOrderActivity activity) {
        activity.setPurchaseOrder(this);
        activities.add(activity);
    }
}
