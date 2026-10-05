package com.intelliatech.app.entity;

import jakarta.persistence.*;
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
@Table(name = "bills", uniqueConstraints = @UniqueConstraint(name = "uk_bills_org_number", columnNames = {"organization_id", "bill_number"}))
@EntityListeners(AuditingEntityListener.class)
public class Bill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long organizationId = 1L;
    @Column(nullable = false, length = 64) private String billNumber;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vendor_id") private Vendor vendor;
    @Column(nullable = false, length = 160) private String vendorName;
    @Column(nullable = false) private LocalDate billDate;
    @Column(nullable = false) private LocalDate dueDate;
    @Column(length = 100) private String referenceNumber;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "purchase_order_id") private PurchaseOrder purchaseOrder;
    @Column(length = 64) private String purchaseOrderNumber;
    @Column(length = 64) private String paymentTerms;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "place_of_supply") private SupplyState placeOfSupply;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_of_supply") private SupplyState sourceOfSupply;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "destination_of_supply") private SupplyState destinationOfSupply;
    @Enumerated(EnumType.STRING) @Column(length = 48) private GstTreatment gstTreatment;
    @Column(nullable = false, length = 16) private String currencyCode = "INR";
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal exchangeRate = BigDecimal.ONE;
    @Column(length = 255) private String subject;
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
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amountPaid = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal balanceDue = BigDecimal.ZERO;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(columnDefinition = "TEXT") private String termsAndConditions;
    @Column(length = 255) private String attachmentName;
    @Column(length = 1000) private String attachmentUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private BillStatus status = BillStatus.DRAFT;
    private LocalDate expectedPaymentDate;
    private LocalDateTime openedAt;
    @Column(length = 120) private String openedBy;
    private LocalDateTime paidAt;
    private LocalDateTime voidedAt;
    @Column(length = 120) private String voidedBy;
    @Column(length = 1000) private String voidReason;
    @Column(nullable = false, length = 120) private String createdBy = "Admin";
    @Column(nullable = false, length = 120) private String updatedBy = "Admin";
    @Column(nullable = false) private boolean deleted;
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false) private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc") private List<BillItem> items = new ArrayList<>();
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paymentDate desc, id desc") private List<BillPayment> payments = new ArrayList<>();
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt desc") private List<BillActivity> activities = new ArrayList<>();

    public void replaceItems(List<BillItem> replacements) {
        items.clear();
        replacements.forEach(item -> { item.setBill(this); items.add(item); });
    }

    public void addPayment(BillPayment payment) { payment.setBill(this); payments.add(payment); }
    public void addActivity(BillActivity activity) { activity.setBill(this); activities.add(activity); }
}

