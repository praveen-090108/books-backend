package com.intelliatech.app.dto.response;

import com.intelliatech.app.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record BillResponse(
        Long id, String billNumber, Long vendorId, String vendorName, String vendorGstin,
        String vendorEmail, String vendorPhone, PurchaseOrderAddressResponse vendorAddress,
        LocalDate billDate, LocalDate dueDate, String referenceNumber,
        Long purchaseOrderId, String purchaseOrderNumber, String paymentTerms,
        String placeOfSupplyCode, String placeOfSupplyName,
        String sourceOfSupplyCode, String sourceOfSupplyName,
        String destinationOfSupplyCode, String destinationOfSupplyName,
        GstTreatment gstTreatment, String currencyCode, BigDecimal exchangeRate,
        String subject, ExpenseAmountType amountType, BillStatus status,
        BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxableAmount,
        BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal igstAmount,
        BigDecimal cessAmount, BigDecimal shippingCharge, BigDecimal adjustmentAmount,
        BigDecimal roundOffAmount, BigDecimal totalTaxAmount, BigDecimal totalAmount,
        BigDecimal amountPaid, BigDecimal balanceDue, String amountInWords,
        String notes, String termsAndConditions, String attachmentName, String attachmentUrl,
        LocalDate expectedPaymentDate, LocalDateTime openedAt, LocalDateTime paidAt,
        LocalDateTime voidedAt, String voidReason, String createdBy, String updatedBy,
        LocalDateTime createdAt, LocalDateTime updatedAt, List<BillItemResponse> items,
        List<BillPaymentResponse> payments, List<BillActivityResponse> activities,
        Set<String> availableActions
) {}

