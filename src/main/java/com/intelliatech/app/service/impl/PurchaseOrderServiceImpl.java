package com.intelliatech.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.PurchaseOrderAddressRequest;
import com.intelliatech.app.dto.request.PurchaseOrderEmailRequest;
import com.intelliatech.app.dto.request.PurchaseOrderItemRequest;
import com.intelliatech.app.dto.request.PurchaseOrderReceiveRequest;
import com.intelliatech.app.dto.request.PurchaseOrderRequest;
import com.intelliatech.app.dto.request.PurchaseOrderStatusRequest;
import com.intelliatech.app.dto.response.ExpenseTaxSummaryResponse;
import com.intelliatech.app.dto.response.PurchaseOrderActivityResponse;
import com.intelliatech.app.dto.response.PurchaseOrderAddressResponse;
import com.intelliatech.app.dto.response.PurchaseOrderFiltersResponse;
import com.intelliatech.app.dto.response.PurchaseOrderItemResponse;
import com.intelliatech.app.dto.response.PurchaseOrderResponse;
import com.intelliatech.app.dto.response.PurchaseOrderSummaryResponse;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.ExpenseAmountType;
import com.intelliatech.app.entity.GstTreatment;
import com.intelliatech.app.entity.PurchaseOrder;
import com.intelliatech.app.entity.PurchaseOrderActivity;
import com.intelliatech.app.entity.PurchaseOrderDiscountType;
import com.intelliatech.app.entity.PurchaseOrderItem;
import com.intelliatech.app.entity.PurchaseOrderStatus;
import com.intelliatech.app.entity.SupplyState;
import com.intelliatech.app.entity.TaxRate;
import com.intelliatech.app.entity.Vendor;
import com.intelliatech.app.entity.VendorStatus;
import com.intelliatech.app.exception.ResourceConflictException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.PurchaseOrderRepository;
import com.intelliatech.app.repository.SupplyStateRepository;
import com.intelliatech.app.repository.TaxRateRepository;
import com.intelliatech.app.repository.VendorRepository;
import com.intelliatech.app.service.DocumentNumberPreferenceService;
import com.intelliatech.app.service.ExpenseTaxCalculator;
import com.intelliatech.app.service.PurchaseOrderService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.JoinType;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {
    private static final Long ORGANIZATION_ID = 1L;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VendorRepository vendorRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final TaxRateRepository taxRateRepository;
    private final SupplyStateRepository supplyStateRepository;
    private final ExpenseTaxCalculator taxCalculator;
    private final DocumentNumberPreferenceService numberService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> findAll(String search, Long vendorId, String status, String source,
            String destination, String currency, String gstTreatment, String createdBy,
            LocalDate dateFrom, LocalDate dateTo, LocalDate expectedDeliveryFrom,
            LocalDate expectedDeliveryTo, BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {
        return purchaseOrderRepository.findAll(specification(search, vendorId, status, source, destination,
                currency, gstTreatment, createdBy, dateFrom, dateTo, expectedDeliveryFrom,
                expectedDeliveryTo, minAmount, maxAmount), pageable).map(this::toResponse);
    }

    @Override @Transactional(readOnly = true)
    public PurchaseOrderResponse findById(Long id) { return toResponse(getOrder(id)); }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderSummaryResponse summary() {
        List<PurchaseOrder> orders = purchaseOrderRepository.findAllByOrganizationIdAndDeletedFalse(ORGANIZATION_ID);
        long drafts = 0, issued = 0, partial = 0, received = 0, billed = 0;
        BigDecimal total = ZERO, outstanding = ZERO, month = ZERO;
        YearMonth currentMonth = YearMonth.now();
        for (PurchaseOrder order : orders) {
            total = total.add(order.getTotalAmount());
            if (YearMonth.from(order.getPurchaseOrderDate()).equals(currentMonth)) month = month.add(order.getTotalAmount());
            switch (order.getStatus()) {
                case DRAFT -> drafts++;
                case ISSUED -> issued++;
                case PARTIALLY_RECEIVED -> partial++;
                case RECEIVED -> received++;
                case BILLED, PARTIALLY_BILLED -> billed++;
                default -> { }
            }
            if (!EnumSet.of(PurchaseOrderStatus.BILLED, PurchaseOrderStatus.CLOSED,
                    PurchaseOrderStatus.CANCELLED).contains(order.getStatus())) outstanding = outstanding.add(order.getTotalAmount());
        }
        return new PurchaseOrderSummaryResponse(orders.size(), drafts, issued, partial, received, billed,
                money(total), money(outstanding), money(month));
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderFiltersResponse filters() {
        OrganizationProfile profile = organizationProfile();
        List<PurchaseOrderFiltersResponse.VendorOption> vendors = vendorRepository.findAll().stream()
                .filter(vendor -> ORGANIZATION_ID.equals(vendor.getOrganizationId()) && vendor.getStatus() == VendorStatus.ACTIVE)
                .map(vendor -> new PurchaseOrderFiltersResponse.VendorOption(vendor.getId(), vendor.getVendorNumber(),
                        vendor.getVendorName(), vendor.getDisplayName(), vendor.getGstin(), vendor.getTaxTreatment(),
                        vendor.getSourceOfSupply(), vendor.getCurrency(), vendor.getPaymentTerms(), vendor.getPrimaryContact(),
                        vendor.getEmail(), vendor.getPhone(), vendorAddress(vendor)))
                .toList();
        List<PurchaseOrderFiltersResponse.ItemOption> items = businessRecordRepository.findAll().stream()
                .filter(record -> "purchases".equals(record.getModule()) && "items".equals(record.getType()))
                .filter(record -> !"INACTIVE".equalsIgnoreCase(record.getStatus()) && !"DELETED".equalsIgnoreCase(record.getStatus()))
                .map(this::itemOption).toList();
        List<PurchaseOrderFiltersResponse.TaxOption> taxes = taxRateRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(tax -> new PurchaseOrderFiltersResponse.TaxOption(tax.getId(), tax.getCode(), tax.getName(),
                        tax.getRate(), tax.getTaxCategory())).toList();
        List<PurchaseOrderFiltersResponse.StateOption> states = supplyStateRepository.findAllByOrderByNameAsc().stream()
                .map(state -> new PurchaseOrderFiltersResponse.StateOption(state.getCode(), state.getName(), state.getTerritoryType()))
                .toList();
        return new PurchaseOrderFiltersResponse(vendors, items, taxes, states,
                java.util.Arrays.stream(GstTreatment.values()).map(Enum::name).toList(),
                java.util.Arrays.stream(PurchaseOrderStatus.values()).map(Enum::name).toList(),
                profile.stateCode(), profile.stateName(), profile.country());
    }

    @Override
    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request, String action) {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrganizationId(ORGANIZATION_ID);
        order.setPurchaseOrderNumber(nextNumber());
        order.setCreatedBy(currentUser());
        order.setUpdatedBy(currentUser());
        copy(request, order);
        activity(order, "CREATED", "Purchase Order created as Draft.");
        order = purchaseOrderRepository.saveAndFlush(order);
        if ("issue".equalsIgnoreCase(action) || "send".equalsIgnoreCase(action)) {
            transitionToIssued(order, "send".equalsIgnoreCase(action) ? "Purchase Order sent to vendor." : "Purchase Order issued.");
        }
        return toResponse(order);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse update(Long id, PurchaseOrderRequest request) {
        PurchaseOrder order = getOrderForUpdate(id);
        if (!EnumSet.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ISSUED).contains(order.getStatus())) {
            throw new ResourceConflictException("Only Draft or Issued Purchase Orders can be edited.");
        }
        copy(request, order);
        order.setUpdatedBy(currentUser());
        activity(order, "UPDATED", "Purchase Order details updated.");
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Override @Transactional
    public PurchaseOrderResponse issue(Long id) {
        PurchaseOrder order = getOrderForUpdate(id);
        transitionToIssued(order, "Purchase Order marked as Issued.");
        return toResponse(order);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse receive(Long id, PurchaseOrderReceiveRequest request) {
        PurchaseOrder order = getOrderForUpdate(id);
        if (!EnumSet.of(PurchaseOrderStatus.ISSUED, PurchaseOrderStatus.PARTIALLY_RECEIVED).contains(order.getStatus())) {
            throw new ResourceConflictException("Only Issued or Partially Received Purchase Orders can receive goods.");
        }
        if (request.items() == null || request.items().isEmpty()) throw new IllegalArgumentException("Enter at least one received quantity.");
        java.util.Map<Long, BigDecimal> quantities = request.items().stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseOrderReceiveRequest.ReceivedItem::purchaseOrderItemId,
                        PurchaseOrderReceiveRequest.ReceivedItem::receivedQuantity, BigDecimal::add));
        boolean changed = false;
        for (PurchaseOrderItem item : order.getItems()) {
            BigDecimal received = quantities.get(item.getId());
            if (received == null || received.signum() == 0) continue;
            if (received.signum() < 0) throw new IllegalArgumentException("Received quantity cannot be negative.");
            BigDecimal next = item.getReceivedQuantity().add(received);
            if (next.compareTo(item.getQuantity()) > 0) throw new IllegalArgumentException("Received quantity cannot exceed ordered quantity for " + item.getItemName() + ".");
            item.setReceivedQuantity(next);
            changed = true;
        }
        if (!changed) throw new IllegalArgumentException("Enter a received quantity greater than zero.");
        boolean complete = order.getItems().stream().allMatch(item -> item.getReceivedQuantity().compareTo(item.getQuantity()) >= 0);
        order.setStatus(complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        order.setReceivedAt(request.receivedDate().atStartOfDay());
        order.setReceivedBy(currentUser());
        if (StringUtils.hasText(request.warehouse())) order.setWarehouseName(request.warehouse().trim());
        order.setUpdatedBy(currentUser());
        activity(order, complete ? "RECEIVED" : "PARTIALLY_RECEIVED", clean(request.notes()));
        return toResponse(order);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cloneOrder(Long id) {
        PurchaseOrder source = getOrder(id);
        PurchaseOrder clone = new PurchaseOrder();
        clone.setOrganizationId(ORGANIZATION_ID);
        clone.setPurchaseOrderNumber(nextNumber());
        clone.setCreatedBy(currentUser());
        clone.setUpdatedBy(currentUser());
        clone.setPurchaseOrderDate(LocalDate.now());
        clone.setExpectedDeliveryDate(source.getExpectedDeliveryDate());
        clone.setVendor(source.getVendor());
        clone.setVendorName(source.getVendorName());
        clone.setVendorAddressJson(source.getVendorAddressJson());
        clone.setDeliveryAddressJson(source.getDeliveryAddressJson());
        clone.setDeliveryAddressSource(source.getDeliveryAddressSource());
        clone.setReferenceNumber(null);
        clone.setShipmentPreference(source.getShipmentPreference());
        clone.setPaymentTerms(source.getPaymentTerms());
        clone.setCurrencyCode(source.getCurrencyCode());
        clone.setExchangeRate(source.getExchangeRate());
        clone.setGstTreatment(source.getGstTreatment());
        clone.setSourceOfSupply(source.getSourceOfSupply());
        clone.setDestinationOfSupply(source.getDestinationOfSupply());
        clone.setPlaceOfSupply(source.getPlaceOfSupply());
        clone.setProjectName(source.getProjectName()); clone.setBranchName(source.getBranchName());
        clone.setWarehouseName(source.getWarehouseName()); clone.setAttention(source.getAttention());
        clone.setAmountType(source.getAmountType()); clone.setShippingCharge(source.getShippingCharge());
        clone.setAdjustmentAmount(source.getAdjustmentAmount()); clone.setNotes(source.getNotes());
        clone.setTermsAndConditions(source.getTermsAndConditions());
        List<PurchaseOrderItem> items = source.getItems().stream().map(this::cloneItem).toList();
        clone.replaceItems(items);
        recalculate(clone);
        activity(clone, "CLONED", "Cloned from " + source.getPurchaseOrderNumber() + ".");
        return toResponse(purchaseOrderRepository.saveAndFlush(clone));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse convertToBill(Long id) {
        PurchaseOrder order = getOrderForUpdate(id);
        if (!EnumSet.of(PurchaseOrderStatus.ISSUED, PurchaseOrderStatus.PARTIALLY_RECEIVED,
                PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.PARTIALLY_BILLED).contains(order.getStatus())) {
            throw new ResourceConflictException("This Purchase Order cannot be converted to a Bill in its current status.");
        }
        if (order.getLinkedBillId() != null) throw new ResourceConflictException("A Bill has already been created for this Purchase Order.");
        BusinessRecord bill = new BusinessRecord();
        bill.setModule("purchases"); bill.setType("bills");
        bill.setRecordNumber(nextLegacyNumber("BILL", "bills"));
        bill.setPartyName(order.getVendorName());
        bill.setPartyEmail(order.getVendor() == null ? null : order.getVendor().getEmail());
        bill.setPartyPhone(order.getVendor() == null ? null : order.getVendor().getPhone());
        bill.setPartyCity(order.getSourceOfSupply() == null ? null : order.getSourceOfSupply().getName());
        bill.setCategory("Purchase Order"); bill.setStatus("Due"); bill.setSecondaryStatus("Unpaid");
        bill.setAmount(order.getTotalAmount()); bill.setBalanceAmount(order.getTotalAmount());
        bill.setRecordDate(LocalDate.now()); bill.setDueDate(LocalDate.now().plusDays(30));
        bill.setReferenceNumber(order.getPurchaseOrderNumber()); bill.setOwnerName(currentUser());
        bill.setNotes(order.getNotes());
        bill = businessRecordRepository.saveAndFlush(bill);
        order.setLinkedBillId(bill.getId()); order.setLinkedBillNumber(bill.getRecordNumber());
        order.setStatus(PurchaseOrderStatus.BILLED); order.setUpdatedBy(currentUser());
        order.getItems().forEach(item -> item.setBilledQuantity(item.getQuantity()));
        activity(order, "CONVERTED_TO_BILL", "Created Bill " + bill.getRecordNumber() + ".");
        return toResponse(order);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse sendEmail(Long id, PurchaseOrderEmailRequest request) {
        PurchaseOrder order = getOrderForUpdate(id);
        if (!StringUtils.hasText(request.to())) throw new IllegalArgumentException("Vendor email is required.");
        transitionToIssued(order, "Email queued for " + request.to().trim() + ".");
        activity(order, "EMAIL_QUEUED", clean(request.subject()));
        return toResponse(order);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancel(Long id, PurchaseOrderStatusRequest request) {
        PurchaseOrder order = getOrderForUpdate(id);
        if (!EnumSet.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ISSUED).contains(order.getStatus())) {
            throw new ResourceConflictException("Only Draft or Issued Purchase Orders can be cancelled.");
        }
        if (order.getLinkedBillId() != null) {
            throw new ResourceConflictException("A Purchase Order linked to a Bill cannot be cancelled.");
        }
        if (order.getItems().stream().anyMatch(item -> item.getReceivedQuantity().signum() > 0)) {
            throw new ResourceConflictException("A Purchase Order with received items cannot be cancelled.");
        }
        String reason = clean(request.reason());
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(currentUser());
        order.setCancellationReason(reason);
        order.setUpdatedBy(currentUser());
        activity(order, "CANCELLED", StringUtils.hasText(reason) ? reason : "Purchase Order cancelled.");
        return toResponse(order);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse close(Long id, PurchaseOrderStatusRequest request) {
        PurchaseOrder order = getOrderForUpdate(id);
        if (!EnumSet.of(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.PARTIALLY_BILLED,
                PurchaseOrderStatus.BILLED).contains(order.getStatus())) {
            throw new ResourceConflictException("Only Received or Billed Purchase Orders can be closed.");
        }
        String reason = clean(request.reason());
        order.setStatus(PurchaseOrderStatus.CLOSED);
        order.setClosedAt(LocalDateTime.now());
        order.setClosedBy(currentUser());
        order.setClosingReason(reason);
        order.setUpdatedBy(currentUser());
        activity(order, "CLOSED", StringUtils.hasText(reason) ? reason : "Purchase Order closed.");
        return toResponse(order);
    }

    @Override @Transactional(readOnly = true)
    public byte[] pdf(Long id) { return buildPdf(getOrder(id)); }

    @Override
    @Transactional
    public void delete(Long id) {
        PurchaseOrder order = getOrderForUpdate(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) throw new ResourceConflictException("Only Draft Purchase Orders can be deleted.");
        if (order.getLinkedBillId() != null) throw new ResourceConflictException("This Purchase Order is linked to a Bill and cannot be deleted.");
        order.setDeleted(true); order.setUpdatedBy(currentUser());
        activity(order, "DELETED", "Purchase Order deleted.");
    }

    private void copy(PurchaseOrderRequest request, PurchaseOrder order) {
        Vendor vendor = vendorRepository.findByIdAndOrganizationId(request.vendorId(), ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found."));
        if (vendor.getStatus() != VendorStatus.ACTIVE) throw new IllegalArgumentException("Only active Vendors can be selected.");
        SupplyState source = state(request.sourceOfSupplyCode(), "Source of Supply");
        SupplyState destination = state(request.destinationOfSupplyCode(), "Destination of Supply");
        SupplyState place = state(StringUtils.hasText(request.placeOfSupplyCode()) ? request.placeOfSupplyCode() : destination.getCode(), "Place of Supply");
        if (request.expectedDeliveryDate() != null && request.expectedDeliveryDate().isBefore(request.purchaseOrderDate())) {
            throw new IllegalArgumentException("Expected Delivery Date cannot be before the Purchase Order Date.");
        }
        order.setPurchaseOrderDate(request.purchaseOrderDate()); order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setVendor(vendor); order.setVendorName(vendor.getVendorName());
        order.setVendorAddressJson(json(request.vendorAddress() == null ? vendorAddressRequest(vendor) : request.vendorAddress()));
        order.setDeliveryAddressJson(json(request.deliveryAddress())); order.setDeliveryAddressSource(clean(request.deliveryAddressSource()));
        order.setReferenceNumber(clean(request.referenceNumber())); order.setShipmentPreference(clean(request.shipmentPreference()));
        order.setPaymentTerms(clean(request.paymentTerms())); order.setCurrencyCode(request.currencyCode().trim().toUpperCase(Locale.ROOT));
        order.setExchangeRate(request.exchangeRate()); order.setGstTreatment(request.gstTreatment());
        order.setSourceOfSupply(source); order.setDestinationOfSupply(destination); order.setPlaceOfSupply(place);
        order.setProjectName(clean(request.projectName())); order.setBranchName(clean(request.branchName()));
        order.setWarehouseName(clean(request.warehouseName())); order.setAttention(clean(request.attention()));
        order.setAmountType(request.amountType()); order.setShippingCharge(money(request.shippingCharge()));
        order.setAdjustmentAmount(money(request.adjustmentAmount())); order.setNotes(clean(request.notes()));
        order.setTermsAndConditions(clean(request.termsAndConditions())); order.setAttachmentName(clean(request.attachmentName()));
        order.setAttachmentUrl(clean(request.attachmentUrl()));
        List<PurchaseOrderItem> items = new ArrayList<>();
        for (int index = 0; index < request.items().size(); index++) items.add(toItem(request.items().get(index), order, index));
        order.replaceItems(items); recalculate(order);
    }

    private PurchaseOrderItem toItem(PurchaseOrderItemRequest request, PurchaseOrder order, int index) {
        BusinessRecord master = businessRecordRepository.findByModuleAndTypeAndId("purchases", "items", request.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found."));
        ItemMetadata metadata = itemMetadata(master);
        TaxRate tax = request.taxId() == null ? null : taxRateRepository.findById(request.taxId())
                .filter(TaxRate::isActive).orElseThrow(() -> new ResourceNotFoundException("Tax rate not found."));
        PurchaseOrderDiscountType discountType = request.discountType() == null ? PurchaseOrderDiscountType.NONE : request.discountType();
        BigDecimal discountValue = decimal(request.discountValue());
        BigDecimal gross = request.quantity().multiply(request.rate());
        BigDecimal discount = switch (discountType) {
            case NONE -> ZERO;
            case PERCENTAGE -> {
                if (discountValue.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("Percentage discount cannot exceed 100.");
                yield gross.multiply(discountValue).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            }
            case FLAT_AMOUNT -> discountValue;
        };
        if (discount.compareTo(gross) > 0) throw new IllegalArgumentException("Discount cannot exceed item amount for " + master.getPartyName() + ".");
        BigDecimal discounted = gross.subtract(discount);
        ExpenseTaxSummaryResponse taxSummary = taxCalculator.calculate(discounted, order.getAmountType(), order.getGstTreatment(),
                order.getSourceOfSupply().getCode(), order.getDestinationOfSupply().getCode(), tax);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setItemId(master.getId()); item.setItemName(master.getPartyName()); item.setItemSku(master.getReferenceNumber());
        item.setItemType(metadata.itemType()); item.setDescription(firstNonBlank(request.description(), metadata.description(), master.getNotes()));
        item.setAccountName(firstNonBlank(request.accountName(), metadata.accountName(), "Purchases"));
        item.setHsnCode(firstNonBlank(request.hsnCode(), metadata.hsnCode())); item.setSacCode(firstNonBlank(request.sacCode(), metadata.sacCode()));
        item.setQuantity(request.quantity()); item.setUnit(firstNonBlank(request.unit(), metadata.unit(), "Nos")); item.setRate(request.rate());
        item.setDiscountType(discountType); item.setDiscountValue(discountValue); item.setDiscountAmount(money(discount));
        item.setTax(tax); item.setTaxName(tax == null ? null : tax.getName()); item.setTaxRate(tax == null ? BigDecimal.ZERO : tax.getRate());
        item.setTaxableAmount(taxSummary.taxableAmount()); item.setCgstAmount(taxSummary.cgstAmount());
        item.setSgstAmount(taxSummary.sgstAmount()); item.setIgstAmount(taxSummary.igstAmount());
        item.setCessAmount(taxSummary.cessAmount()); item.setLineTotal(taxSummary.totalAmount());
        item.setWarehouseName(clean(request.warehouseName())); item.setProjectName(clean(request.projectName())); item.setSortOrder(index);
        return item;
    }

    private void recalculate(PurchaseOrder order) {
        BigDecimal subtotal = ZERO, discount = ZERO, taxable = ZERO, cgst = ZERO, sgst = ZERO, igst = ZERO, cess = ZERO;
        for (PurchaseOrderItem item : order.getItems()) {
            subtotal = subtotal.add(item.getQuantity().multiply(item.getRate())); discount = discount.add(item.getDiscountAmount());
            taxable = taxable.add(item.getTaxableAmount()); cgst = cgst.add(item.getCgstAmount()); sgst = sgst.add(item.getSgstAmount());
            igst = igst.add(item.getIgstAmount()); cess = cess.add(item.getCessAmount());
        }
        BigDecimal tax = cgst.add(sgst).add(igst).add(cess);
        order.setSubtotal(money(subtotal)); order.setDiscountAmount(money(discount)); order.setTaxableAmount(money(taxable));
        order.setCgstAmount(money(cgst)); order.setSgstAmount(money(sgst)); order.setIgstAmount(money(igst)); order.setCessAmount(money(cess));
        order.setTotalTaxAmount(money(tax)); order.setRoundOffAmount(ZERO);
        order.setTotalAmount(money(taxable.add(tax).add(order.getShippingCharge()).add(order.getAdjustmentAmount())));
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder order) {
        Vendor vendor = order.getVendor();
        return new PurchaseOrderResponse(order.getId(), order.getPurchaseOrderNumber(), order.getPurchaseOrderDate(),
                order.getExpectedDeliveryDate(), vendor == null ? null : vendor.getId(), order.getVendorName(),
                vendor == null ? null : vendor.getGstin(), vendor == null ? null : vendor.getEmail(), vendor == null ? null : vendor.getPhone(),
                address(order.getVendorAddressJson()), address(order.getDeliveryAddressJson()), order.getDeliveryAddressSource(),
                order.getReferenceNumber(), order.getShipmentPreference(), order.getPaymentTerms(), order.getCurrencyCode(),
                order.getExchangeRate(), order.getGstTreatment(), code(order.getSourceOfSupply()), name(order.getSourceOfSupply()),
                code(order.getDestinationOfSupply()), name(order.getDestinationOfSupply()), code(order.getPlaceOfSupply()), name(order.getPlaceOfSupply()),
                order.getProjectName(), order.getBranchName(), order.getWarehouseName(), order.getAttention(), order.getStatus(), order.getAmountType(),
                order.getSubtotal(), order.getDiscountAmount(), order.getTaxableAmount(), order.getCgstAmount(), order.getSgstAmount(),
                order.getIgstAmount(), order.getCessAmount(), order.getShippingCharge(), order.getAdjustmentAmount(), order.getRoundOffAmount(),
                order.getTotalTaxAmount(), order.getTotalAmount(), order.getNotes(), order.getTermsAndConditions(), order.getAttachmentName(),
                order.getAttachmentUrl(), order.getLinkedBillId(), order.getLinkedBillNumber(), order.getIssuedAt(), order.getIssuedBy(),
                order.getReceivedAt(), order.getReceivedBy(), order.getCancelledAt(), order.getCancelledBy(),
                order.getCancellationReason(), order.getClosedAt(), order.getClosedBy(), order.getClosingReason(),
                order.getCreatedBy(), order.getUpdatedBy(), order.getCreatedAt(), order.getUpdatedAt(),
                order.getItems().stream().map(this::itemResponse).toList(), order.getActivities().stream().map(activity ->
                    new PurchaseOrderActivityResponse(activity.getId(), activity.getAction(), activity.getDetails(), activity.getPerformedBy(), activity.getCreatedAt())).toList(),
                actions(order));
    }

    private PurchaseOrderItemResponse itemResponse(PurchaseOrderItem item) {
        return new PurchaseOrderItemResponse(item.getId(), item.getItemId(), item.getItemName(), item.getItemSku(), item.getItemType(),
                item.getDescription(), item.getAccountName(), item.getHsnCode(), item.getSacCode(), item.getQuantity(), item.getReceivedQuantity(),
                item.getBilledQuantity(), item.getUnit(), item.getRate(), item.getDiscountType(), item.getDiscountValue(), item.getDiscountAmount(),
                item.getTax() == null ? null : item.getTax().getId(), item.getTaxName(), item.getTaxRate(), item.getTaxableAmount(),
                item.getCgstAmount(), item.getSgstAmount(), item.getIgstAmount(), item.getCessAmount(), item.getLineTotal(),
                item.getWarehouseName(), item.getProjectName());
    }

    private Specification<PurchaseOrder> specification(String search, Long vendorId, String status, String source,
            String destination, String currency, String gstTreatment, String createdBy,
            LocalDate dateFrom, LocalDate dateTo, LocalDate expectedDeliveryFrom,
            LocalDate expectedDeliveryTo, BigDecimal minAmount, BigDecimal maxAmount) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), ORGANIZATION_ID)); predicates.add(builder.isFalse(root.get("deleted")));
            if (StringUtils.hasText(search)) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                var item = root.join("items", JoinType.LEFT);
                query.distinct(true);
                predicates.add(builder.or(builder.like(builder.lower(root.get("purchaseOrderNumber")), term),
                        builder.like(builder.lower(root.get("vendorName")), term), builder.like(builder.lower(root.get("referenceNumber")), term),
                        builder.like(builder.lower(root.get("notes")), term), builder.like(builder.lower(item.get("itemName")), term),
                        builder.like(builder.lower(item.get("itemSku")), term), builder.like(builder.lower(item.get("hsnCode")), term),
                        builder.like(builder.lower(item.get("sacCode")), term), builder.like(builder.lower(item.get("description")), term)));
            }
            if (vendorId != null) predicates.add(builder.equal(root.get("vendor").get("id"), vendorId));
            if (StringUtils.hasText(status)) {
                try { predicates.add(builder.equal(root.get("status"), PurchaseOrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)))); }
                catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unsupported Purchase Order status."); }
            }
            if (StringUtils.hasText(source)) predicates.add(builder.equal(root.get("sourceOfSupply").get("code"), source));
            if (StringUtils.hasText(destination)) predicates.add(builder.equal(root.get("destinationOfSupply").get("code"), destination));
            if (StringUtils.hasText(currency)) predicates.add(builder.equal(builder.upper(root.get("currencyCode")), currency.trim().toUpperCase(Locale.ROOT)));
            if (StringUtils.hasText(gstTreatment)) {
                try { predicates.add(builder.equal(root.get("gstTreatment"), GstTreatment.valueOf(gstTreatment.trim().toUpperCase(Locale.ROOT)))); }
                catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unsupported GST treatment."); }
            }
            if (StringUtils.hasText(createdBy)) predicates.add(builder.like(builder.lower(root.get("createdBy")), "%" + createdBy.trim().toLowerCase(Locale.ROOT) + "%"));
            if (dateFrom != null) predicates.add(builder.greaterThanOrEqualTo(root.get("purchaseOrderDate"), dateFrom));
            if (dateTo != null) predicates.add(builder.lessThanOrEqualTo(root.get("purchaseOrderDate"), dateTo));
            if (expectedDeliveryFrom != null) predicates.add(builder.greaterThanOrEqualTo(root.get("expectedDeliveryDate"), expectedDeliveryFrom));
            if (expectedDeliveryTo != null) predicates.add(builder.lessThanOrEqualTo(root.get("expectedDeliveryDate"), expectedDeliveryTo));
            if (minAmount != null) predicates.add(builder.greaterThanOrEqualTo(root.get("totalAmount"), minAmount));
            if (maxAmount != null) predicates.add(builder.lessThanOrEqualTo(root.get("totalAmount"), maxAmount));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Set<String> actions(PurchaseOrder order) {
        Set<String> actions = new LinkedHashSet<>(); actions.add("VIEW"); actions.add("PDF"); actions.add("PRINT"); actions.add("CLONE");
        if (EnumSet.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ISSUED).contains(order.getStatus())) actions.add("EDIT");
        if (order.getStatus() == PurchaseOrderStatus.DRAFT) { actions.add("ISSUE"); actions.add("DELETE"); actions.add("CANCEL"); }
        if (order.getStatus() == PurchaseOrderStatus.ISSUED) actions.add("CANCEL");
        if (EnumSet.of(PurchaseOrderStatus.ISSUED, PurchaseOrderStatus.PARTIALLY_RECEIVED).contains(order.getStatus())) actions.add("RECEIVE");
        if (EnumSet.of(PurchaseOrderStatus.ISSUED, PurchaseOrderStatus.PARTIALLY_RECEIVED,
                PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.PARTIALLY_BILLED).contains(order.getStatus()) && order.getLinkedBillId() == null) actions.add("CONVERT_TO_BILL");
        if (EnumSet.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ISSUED).contains(order.getStatus())) actions.add("SEND");
        if (EnumSet.of(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.PARTIALLY_BILLED,
                PurchaseOrderStatus.BILLED).contains(order.getStatus())) actions.add("CLOSE");
        return actions;
    }

    private void transitionToIssued(PurchaseOrder order, String details) {
        if (!EnumSet.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ISSUED).contains(order.getStatus()))
            throw new ResourceConflictException("This Purchase Order cannot be issued in its current status.");
        order.setStatus(PurchaseOrderStatus.ISSUED);
        if (order.getIssuedAt() == null) order.setIssuedAt(LocalDateTime.now());
        if (!StringUtils.hasText(order.getIssuedBy())) order.setIssuedBy(currentUser());
        order.setUpdatedBy(currentUser()); activity(order, "ISSUED", details);
    }

    private PurchaseOrder getOrder(Long id) { return purchaseOrderRepository.findByIdAndOrganizationIdAndDeletedFalse(id, ORGANIZATION_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found.")); }
    private PurchaseOrder getOrderForUpdate(Long id) { return purchaseOrderRepository.findForUpdate(id, ORGANIZATION_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found.")); }
    private SupplyState state(String code, String label) { return supplyStateRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException(label + " is not valid.")); }
    private void activity(PurchaseOrder order, String action, String details) { PurchaseOrderActivity activity = new PurchaseOrderActivity();
        activity.setAction(action); activity.setDetails(details); activity.setPerformedBy(currentUser()); order.addActivity(activity); }

    private String nextNumber() {
        String number;
        do { number = numberService.allocateForCreate("purchaseOrders"); }
        while (purchaseOrderRepository.existsByOrganizationIdAndPurchaseOrderNumber(ORGANIZATION_ID, number));
        return number;
    }

    private String nextLegacyNumber(String prefix, String type) {
        long next = businessRecordRepository.findRecordNumbersByModuleAndType("purchases", type).stream()
                .map(number -> number == null ? "" : number.replaceAll("\\D", ""))
                .filter(StringUtils::hasText).mapToLong(value -> { try { return Long.parseLong(value); } catch (NumberFormatException e) { return 0; } }).max().orElse(0) + 1;
        return "%s-%06d".formatted(prefix, next);
    }

    private PurchaseOrderItem cloneItem(PurchaseOrderItem source) { PurchaseOrderItem item = new PurchaseOrderItem();
        item.setItemId(source.getItemId()); item.setItemName(source.getItemName()); item.setItemSku(source.getItemSku()); item.setItemType(source.getItemType());
        item.setDescription(source.getDescription()); item.setAccountName(source.getAccountName()); item.setHsnCode(source.getHsnCode()); item.setSacCode(source.getSacCode());
        item.setQuantity(source.getQuantity()); item.setUnit(source.getUnit()); item.setRate(source.getRate()); item.setDiscountType(source.getDiscountType());
        item.setDiscountValue(source.getDiscountValue()); item.setDiscountAmount(source.getDiscountAmount()); item.setTax(source.getTax()); item.setTaxName(source.getTaxName());
        item.setTaxRate(source.getTaxRate()); item.setTaxableAmount(source.getTaxableAmount()); item.setCgstAmount(source.getCgstAmount());
        item.setSgstAmount(source.getSgstAmount()); item.setIgstAmount(source.getIgstAmount()); item.setCessAmount(source.getCessAmount());
        item.setLineTotal(source.getLineTotal()); item.setWarehouseName(source.getWarehouseName()); item.setProjectName(source.getProjectName()); item.setSortOrder(source.getSortOrder()); return item; }

    private PurchaseOrderFiltersResponse.ItemOption itemOption(BusinessRecord record) { ItemMetadata metadata = itemMetadata(record);
        TaxRate tax = metadata.taxId() == null ? null : taxRateRepository.findById(metadata.taxId()).orElse(null);
        return new PurchaseOrderFiltersResponse.ItemOption(record.getId(), record.getRecordNumber(), record.getPartyName(), record.getReferenceNumber(),
                metadata.description(), metadata.itemType(), metadata.hsnCode(), metadata.sacCode(), metadata.unit(),
                metadata.purchaseRate().signum() > 0 ? metadata.purchaseRate() : record.getAmount(), tax == null ? null : tax.getId(),
                tax == null ? BigDecimal.ZERO : tax.getRate(), tax == null ? "" : tax.getName(), metadata.accountName(), record.getStatus()); }

    private ItemMetadata itemMetadata(BusinessRecord record) {
        JsonNode node = jsonNode(record.getNotes());
        return new ItemMetadata(text(node, "Item Type", "itemType"), text(node, "Description", "description"),
                text(node, "HSN Code", "hsnCode"), text(node, "SAC Code", "sacCode"), text(node, "Unit", "unit"),
                text(node, "Purchase Account", "accountName"), decimalNode(node, "Cost Price (₹)", "costPrice", "purchaseRate"),
                longNode(node, "taxId"));
    }

    private OrganizationProfile organizationProfile() { BusinessRecord record = businessRecordRepository
            .findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization").orElse(null);
        String stateValue = "", country = "India";
        if (record != null) { JsonNode node = jsonNode(record.getNotes()); stateValue = node.path("state").asText(""); country = node.path("country").asText("India"); }
        String code = extractStateCode(stateValue); SupplyState state = code == null ? supplyStateRepository.findFirstByNameIgnoreCase(stripStateCode(stateValue)).orElse(null)
                : supplyStateRepository.findById(code).orElse(null);
        return new OrganizationProfile(state == null ? null : state.getCode(), state == null ? "" : state.getName(), country); }

    private byte[] buildPdf(PurchaseOrder order) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 34, 34, 36, 48);
            PurchaseOrderCompany company = purchaseOrderCompany();
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PurchaseOrderPageFooter(company.footer()));
            document.open();

            Color ink = new Color(31, 41, 55);
            Color muted = new Color(100, 116, 139);
            Color red = new Color(220, 38, 38);
            Color headerBackground = new Color(30, 41, 59);
            Color border = new Color(203, 213, 225);
            Font body = purchaseOrderFont(8.2f, false, ink);
            Font bodyBold = purchaseOrderFont(8.2f, true, ink);
            Font small = purchaseOrderFont(7.4f, false, muted);
            Font label = purchaseOrderFont(8.4f, true, muted);

            addPurchaseOrderHeader(document, order, company, body, bodyBold, red, ink);
            addPurchaseOrderParties(document, order, body, bodyBold, label, border);
            addPurchaseOrderItems(document, order, body, bodyBold, headerBackground, border);
            addPurchaseOrderSettlement(document, order, body, bodyBold, small, border);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Purchase Order PDF could not be generated.", exception);
        }
    }

    private void addPurchaseOrderHeader(Document document, PurchaseOrder order, PurchaseOrderCompany company,
            Font body, Font bodyBold, Color red, Color ink) throws Exception {
        PdfPTable header = new PdfPTable(new float[]{3.2f, 2f});
        header.setWidthPercentage(100);
        header.setSpacingAfter(20);

        PdfPCell companyCell = purchaseOrderCell();
        Image logo = purchaseOrderLogo(company.logoUrl());
        if (logo != null) {
            logo.scaleToFit(175, 62);
            logo.setAlignment(Image.ALIGN_LEFT);
            companyCell.addElement(logo);
        } else {
            Paragraph wordmark = new Paragraph("INTELLIATECH", purchaseOrderFont(18, true, ink));
            wordmark.setSpacingAfter(8);
            companyCell.addElement(wordmark);
        }
        Paragraph companyName = new Paragraph(company.name(), purchaseOrderFont(10.5f, true, ink));
        companyName.setSpacingBefore(5);
        companyName.setSpacingAfter(3);
        companyCell.addElement(companyName);
        Paragraph companyDetails = new Paragraph(company.details(), body);
        companyDetails.setLeading(12);
        companyCell.addElement(companyDetails);
        header.addCell(companyCell);

        PdfPCell titleCell = purchaseOrderCell();
        Paragraph title = new Paragraph("PURCHASE ORDER", purchaseOrderFont(21, true, ink));
        title.setAlignment(Element.ALIGN_RIGHT);
        title.setSpacingAfter(8);
        titleCell.addElement(title);
        Paragraph number = new Paragraph("Purchase Order#  " + value(order.getPurchaseOrderNumber()), bodyBold);
        number.setAlignment(Element.ALIGN_RIGHT);
        number.setSpacingAfter(13);
        titleCell.addElement(number);
        Paragraph status = new Paragraph(title(order.getStatus().name()), purchaseOrderFont(8.5f, true, red));
        status.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(status);
        header.addCell(titleCell);
        document.add(header);

        PdfPTable metadata = new PdfPTable(new float[]{1, 1});
        metadata.setWidthPercentage(100);
        metadata.setSpacingAfter(16);
        PdfPTable left = new PdfPTable(new float[]{1.2f, 1.6f});
        addPurchaseOrderMeta(left, "Purchase Order Date", formatDate(order.getPurchaseOrderDate()), body, bodyBold);
        addPurchaseOrderMeta(left, "Expected Delivery", formatDate(order.getExpectedDeliveryDate()), body, bodyBold);
        addPurchaseOrderMeta(left, "Reference", value(order.getReferenceNumber()), body, bodyBold);
        PdfPCell leftCell = purchaseOrderCell(); leftCell.setPaddingRight(18); leftCell.addElement(left); metadata.addCell(leftCell);
        PdfPTable right = new PdfPTable(new float[]{1.1f, 1.6f});
        addPurchaseOrderMeta(right, "Place of Supply", stateName(order), body, bodyBold);
        addPurchaseOrderMeta(right, "Payment Terms", value(order.getPaymentTerms()), body, bodyBold);
        addPurchaseOrderMeta(right, "Shipment", value(order.getShipmentPreference()), body, bodyBold);
        PdfPCell rightCell = purchaseOrderCell(); rightCell.addElement(right); metadata.addCell(rightCell);
        document.add(metadata);
    }

    private void addPurchaseOrderParties(Document document, PurchaseOrder order, Font body, Font bodyBold,
            Font label, Color border) throws Exception {
        PdfPTable parties = new PdfPTable(new float[]{1, 1, 1});
        parties.setWidthPercentage(100);
        parties.setSpacingAfter(18);
        parties.addCell(purchaseOrderAddressCell("Vendor", order.getVendorName(), address(order.getVendorAddressJson()), body, bodyBold, label, border));
        parties.addCell(purchaseOrderAddressCell("Billing Address", order.getVendorName(), address(order.getVendorAddressJson()), body, bodyBold, label, border));
        PurchaseOrderAddressResponse shipping = address(order.getDeliveryAddressJson());
        parties.addCell(purchaseOrderAddressCell("Shipping Address", shipping == null ? "" : value(shipping.attention()), shipping,
                body, bodyBold, label, border));
        document.add(parties);
    }

    private PdfPCell purchaseOrderAddressCell(String heading, String name, PurchaseOrderAddressResponse address,
            Font body, Font bodyBold, Font label, Color border) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(border);
        cell.setPadding(9);
        Paragraph title = new Paragraph(heading, label); title.setSpacingAfter(5); cell.addElement(title);
        if (StringUtils.hasText(name)) { Paragraph party = new Paragraph(name, bodyBold); party.setSpacingAfter(2); cell.addElement(party); }
        Paragraph details = new Paragraph(addressText(address), body); details.setLeading(11.5f); cell.addElement(details);
        return cell;
    }

    private void addPurchaseOrderItems(Document document, PurchaseOrder order, Font body, Font bodyBold,
            Color headerBackground, Color border) throws Exception {
        PdfPTable items = new PdfPTable(new float[]{1.3f, 1.95f, 1f, .55f, .55f, .8f, .72f, .58f, .95f, .95f});
        items.setWidthPercentage(100);
        items.setHeaderRows(1);
        items.setSplitRows(true);
        items.setSplitLate(false);
        addPurchaseOrderItemHeader(items,
                List.of("Item Name", "Description", "HSN/SAC", "Qty", "Unit", "Rate", "Discount", "Tax %", "Tax Amount", "Amount"),
                headerBackground);
        for (PurchaseOrderItem item : order.getItems()) {
            addPurchaseOrderItemRow(items, List.of(
                    value(item.getItemName()),
                    value(item.getDescription()),
                    value(firstNonBlank(item.getHsnCode(), item.getSacCode())),
                    decimalText(item.getQuantity()),
                    value(item.getUnit()),
                    amountWithoutSymbol(item.getRate()),
                    amountWithoutSymbol(item.getDiscountAmount()),
                    decimalText(item.getTaxRate()) + "%",
                    amountWithoutSymbol(decimal(item.getCgstAmount()).add(decimal(item.getSgstAmount())).add(decimal(item.getIgstAmount())).add(decimal(item.getCessAmount()))),
                    amountWithoutSymbol(item.getLineTotal())
            ), body, bodyBold, border);
        }
        document.add(items);
    }

    private void addPurchaseOrderSettlement(Document document, PurchaseOrder order, Font body, Font bodyBold,
            Font small, Color border) throws Exception {
        PdfPTable settlement = new PdfPTable(new float[]{1.12f, .88f});
        settlement.setWidthPercentage(100);
        settlement.setSpacingBefore(10);
        settlement.setKeepTogether(true);

        PdfPCell information = purchaseOrderCell();
        information.setPaddingRight(16);
        Paragraph wordsLabel = new Paragraph("Total Amount in Words", bodyBold); wordsLabel.setSpacingAfter(3); information.addElement(wordsLabel);
        Paragraph words = new Paragraph(amountInWords(order.getTotalAmount(), order.getCurrencyCode()), body); words.setSpacingAfter(14); information.addElement(words);
        if (StringUtils.hasText(order.getTermsAndConditions())) {
            Paragraph termsLabel = new Paragraph("Terms & Conditions", bodyBold); termsLabel.setSpacingAfter(3); information.addElement(termsLabel);
            Paragraph terms = new Paragraph(order.getTermsAndConditions(), small); terms.setLeading(10.5f); terms.setSpacingAfter(10); information.addElement(terms);
        }
        if (StringUtils.hasText(order.getNotes())) {
            Paragraph notesLabel = new Paragraph("Notes", bodyBold); notesLabel.setSpacingAfter(3); information.addElement(notesLabel);
            Paragraph notes = new Paragraph(order.getNotes(), small); notes.setLeading(10.5f); information.addElement(notes);
        }
        settlement.addCell(information);

        PdfPCell summaryCell = purchaseOrderCell();
        PdfPTable totals = new PdfPTable(new float[]{1.45f, 1f}); totals.setWidthPercentage(100);
        addPurchaseOrderTotal(totals, "Subtotal", order.getSubtotal(), order.getCurrencyCode(), body, false, false);
        if (decimal(order.getDiscountAmount()).signum() != 0) addPurchaseOrderTotal(totals, "Discount", order.getDiscountAmount(), order.getCurrencyCode(), body, true, false);
        if (decimal(order.getCgstAmount()).signum() != 0) addPurchaseOrderTotal(totals, "CGST", order.getCgstAmount(), order.getCurrencyCode(), body, false, false);
        if (decimal(order.getSgstAmount()).signum() != 0) addPurchaseOrderTotal(totals, "SGST", order.getSgstAmount(), order.getCurrencyCode(), body, false, false);
        if (decimal(order.getIgstAmount()).signum() != 0) addPurchaseOrderTotal(totals, "IGST", order.getIgstAmount(), order.getCurrencyCode(), body, false, false);
        if (decimal(order.getCessAmount()).signum() != 0) addPurchaseOrderTotal(totals, "Cess", order.getCessAmount(), order.getCurrencyCode(), body, false, false);
        if (decimal(order.getShippingCharge()).signum() != 0) addPurchaseOrderTotal(totals, "Shipping Charges", order.getShippingCharge(), order.getCurrencyCode(), body, false, false);
        if (decimal(order.getAdjustmentAmount()).signum() != 0) addPurchaseOrderTotal(totals, "Adjustment", order.getAdjustmentAmount(), order.getCurrencyCode(), body, false, false);
        if (decimal(order.getRoundOffAmount()).signum() != 0) addPurchaseOrderTotal(totals, "Round Off", order.getRoundOffAmount(), order.getCurrencyCode(), body, false, false);
        addPurchaseOrderTotal(totals, "Grand Total", order.getTotalAmount(), order.getCurrencyCode(), bodyBold, false, true);
        summaryCell.addElement(totals);
        Paragraph signature = new Paragraph("\n\n\n____________________________\nAuthorized Signature", body);
        signature.setAlignment(Element.ALIGN_CENTER); signature.setLeading(14); summaryCell.addElement(signature);
        settlement.addCell(summaryCell);
        document.add(settlement);
    }

    private PurchaseOrderCompany purchaseOrderCompany() {
        BusinessRecord organization = businessRecordRepository
                .findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization").orElse(null);
        BusinessRecord branding = businessRecordRepository
                .findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "branding").orElse(null);
        JsonNode details = jsonNode(organization == null ? null : organization.getNotes());
        JsonNode brandDetails = jsonNode(branding == null ? null : branding.getNotes());
        String name = organization == null ? "IntelliaTech Pvt. Ltd." : value(organization.getPartyName());
        String address = details.path("address").asText(organization == null ? "" : value(organization.getPartyCity()));
        String state = details.path("state").asText("");
        String pincode = details.path("pincode").asText("");
        String country = details.path("country").asText("India");
        String gstin = organization == null ? "" : value(organization.getReferenceNumber());
        String phone = organization == null ? "" : value(organization.getPartyPhone());
        String email = organization == null ? "" : value(organization.getPartyEmail());
        String website = details.path("website").asText("");
        String companyDetails = Stream.of(address, Stream.of(state, pincode).filter(StringUtils::hasText).collect(Collectors.joining(" ")),
                        country, StringUtils.hasText(gstin) ? "GSTIN " + gstin : "", phone, email, website)
                .filter(StringUtils::hasText).collect(Collectors.joining("\n"));
        String footer = Stream.of(name, phone, email, website).filter(StringUtils::hasText).collect(Collectors.joining("  |  "));
        return new PurchaseOrderCompany(name, companyDetails, brandDetails.path("logoUrl").asText(""), footer);
    }

    private Image purchaseOrderLogo(String logoUrl) {
        if (!StringUtils.hasText(logoUrl)) return null;
        try {
            if (logoUrl.startsWith("data:image") && logoUrl.contains(",")) {
                return Image.getInstance(Base64.getDecoder().decode(logoUrl.substring(logoUrl.indexOf(',') + 1)));
            }
            return Image.getInstance(logoUrl);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Font purchaseOrderFont(float size, boolean bold, Color color) {
        List<String> candidates = bold
                ? List.of("/System/Library/Fonts/Supplemental/Arial Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")
                : List.of("/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Supplemental/Arial Unicode.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        for (String candidate : candidates) {
            try {
                if (Files.isRegularFile(Path.of(candidate))) {
                    BaseFont baseFont = BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    return new Font(baseFont, size, Font.NORMAL, color);
                }
            } catch (Exception ignored) {
                // Try the next platform font before falling back to a built-in PDF font.
            }
        }
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size, color);
    }

    private PdfPCell purchaseOrderCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private void addPurchaseOrderMeta(PdfPTable table, String label, String text, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label + "  :", labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER); labelCell.setPadding(3.5f); table.addCell(labelCell);
        PdfPCell valueCell = new PdfPCell(new Phrase(text, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER); valueCell.setPadding(3.5f); valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT); table.addCell(valueCell);
    }

    private void addPurchaseOrderItemHeader(PdfPTable table, List<String> labels, Color background) {
        Font font = purchaseOrderFont(6.8f, true, Color.WHITE);
        for (int index = 0; index < labels.size(); index++) {
            PdfPCell cell = new PdfPCell(new Phrase(labels.get(index), font));
            cell.setBorder(Rectangle.NO_BORDER); cell.setBackgroundColor(background); cell.setPadding(6);
            if (index >= 3) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cell);
        }
    }

    private void addPurchaseOrderItemRow(PdfPTable table, List<String> values, Font font, Font bold, Color border) {
        for (int index = 0; index < values.size(); index++) {
            PdfPCell cell = new PdfPCell(new Phrase(values.get(index), index == 0 ? bold : font));
            cell.setBorder(Rectangle.BOTTOM); cell.setBorderColor(border); cell.setPadding(6); cell.setVerticalAlignment(Element.ALIGN_TOP);
            cell.setNoWrap(false);
            if (index >= 3) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cell);
        }
    }

    private void addPurchaseOrderTotal(PdfPTable table, String label, BigDecimal amount, String currencyCode,
            Font font, boolean negative, boolean highlighted) {
        Color color = negative ? new Color(220, 38, 38) : font.getColor();
        PdfPCell labelCell = new PdfPCell(new Phrase(label, purchaseOrderFont(font.getSize(), highlighted, color)));
        labelCell.setBorder(Rectangle.NO_BORDER); labelCell.setPadding(5.5f);
        if (highlighted) labelCell.setBackgroundColor(new Color(241, 245, 249));
        table.addCell(labelCell);
        String formatted = (negative ? "(-) " : "") + currency(amount, currencyCode);
        PdfPCell valueCell = new PdfPCell(new Phrase(formatted, purchaseOrderFont(font.getSize(), highlighted, color)));
        valueCell.setBorder(Rectangle.NO_BORDER); valueCell.setPadding(5.5f); valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (highlighted) valueCell.setBackgroundColor(new Color(241, 245, 249));
        table.addCell(valueCell);
    }

    private String addressText(PurchaseOrderAddressResponse address) {
        if (address == null) return "";
        return Stream.of(address.attention(), address.addressLine1(), address.addressLine2(),
                        Stream.of(address.city(), address.postalCode()).filter(StringUtils::hasText).collect(Collectors.joining(" ")),
                        address.state(), address.country(), StringUtils.hasText(address.gstin()) ? "GSTIN " + address.gstin() : "",
                        address.phone(), address.email())
                .filter(StringUtils::hasText).collect(Collectors.joining("\n"));
    }

    private String stateName(PurchaseOrder order) {
        SupplyState state = order.getPlaceOfSupply() != null ? order.getPlaceOfSupply() : order.getDestinationOfSupply();
        return state == null ? "" : state.getName() + (StringUtils.hasText(state.getCode()) ? " (" + state.getCode() + ")" : "");
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    private String decimalText(BigDecimal amount) {
        return decimal(amount).stripTrailingZeros().toPlainString();
    }

    private String currency(BigDecimal amount, String currencyCode) {
        String code = StringUtils.hasText(currencyCode) ? currencyCode.trim().toUpperCase(Locale.ROOT) : "INR";
        return ("INR".equals(code) ? "₹" : code + " ") + amountWithoutSymbol(amount);
    }

    private String amountWithoutSymbol(BigDecimal amount) {
        BigDecimal normalized = money(amount).abs();
        String[] parts = normalized.toPlainString().split("\\.");
        String integer = parts[0];
        String grouped;
        if (integer.length() <= 3) grouped = integer;
        else {
            String suffix = integer.substring(integer.length() - 3);
            String prefix = integer.substring(0, integer.length() - 3);
            StringBuilder groups = new StringBuilder();
            while (prefix.length() > 2) { groups.insert(0, "," + prefix.substring(prefix.length() - 2)); prefix = prefix.substring(0, prefix.length() - 2); }
            grouped = prefix + groups + "," + suffix;
        }
        return (money(amount).signum() < 0 ? "-" : "") + grouped + "." + parts[1];
    }

    private String amountInWords(BigDecimal amount, String currencyCode) {
        long whole = money(amount).setScale(0, RoundingMode.HALF_UP).longValue();
        String currency = "INR".equalsIgnoreCase(value(currencyCode)) ? "Rupees" : value(currencyCode);
        return (whole == 0 ? "Zero" : toIndianWords(Math.abs(whole))) + " " + currency + " Only";
    }

    private String toIndianWords(long number) {
        if (number < 20) return List.of("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen").get((int) number);
        if (number < 100) { String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"}; return tens[(int) (number / 10)] + (number % 10 == 0 ? "" : " " + toIndianWords(number % 10)); }
        if (number < 1000) return toIndianWords(number / 100) + " Hundred" + (number % 100 == 0 ? "" : " " + toIndianWords(number % 100));
        if (number < 100000) return toIndianWords(number / 1000) + " Thousand" + (number % 1000 == 0 ? "" : " " + toIndianWords(number % 1000));
        if (number < 10000000) return toIndianWords(number / 100000) + " Lakh" + (number % 100000 == 0 ? "" : " " + toIndianWords(number % 100000));
        return toIndianWords(number / 10000000) + " Crore" + (number % 10000000 == 0 ? "" : " " + toIndianWords(number % 10000000));
    }

    private String title(String text) {
        if (!StringUtils.hasText(text)) return "";
        String normalized = text.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String value(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private record PurchaseOrderCompany(String name, String details, String logoUrl, String footer) { }

    private static final class PurchaseOrderPageFooter extends PdfPageEventHelper {
        private final String companyFooter;
        private PurchaseOrderPageFooter(String companyFooter) { this.companyFooter = companyFooter; }
        @Override public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            canvas.setColorStroke(new Color(203, 213, 225)); canvas.setLineWidth(.5f);
            canvas.moveTo(document.left(), 31); canvas.lineTo(document.right(), 31); canvas.stroke();
            Font footer = FontFactory.getFont(FontFactory.HELVETICA, 7.2f, new Color(100, 116, 139));
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(companyFooter, footer), document.left(), 18, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT, new Phrase("Page " + writer.getPageNumber(), footer), document.right(), 18, 0);
        }
    }

    private PurchaseOrderAddressResponse vendorAddress(Vendor vendor) { return new PurchaseOrderAddressResponse(vendor.getPrimaryContact(), vendor.getBillingAddressLine1(),
            vendor.getBillingAddressLine2(), vendor.getBillingCity(), vendor.getBillingState(), extractStateCode(vendor.getSourceOfSupply()),
            vendor.getBillingCountry(), vendor.getBillingPincode(), vendor.getPhone(), vendor.getEmail(), vendor.getGstin()); }
    private PurchaseOrderAddressRequest vendorAddressRequest(Vendor vendor) { return new PurchaseOrderAddressRequest(vendor.getPrimaryContact(), vendor.getBillingAddressLine1(),
            vendor.getBillingAddressLine2(), vendor.getBillingCity(), vendor.getBillingState(), extractStateCode(vendor.getSourceOfSupply()),
            vendor.getBillingCountry(), vendor.getBillingPincode(), vendor.getPhone(), vendor.getEmail(), vendor.getGstin()); }
    private PurchaseOrderAddressResponse address(String value) { if (!StringUtils.hasText(value)) return null; try { return objectMapper.readValue(value, PurchaseOrderAddressResponse.class); } catch (Exception e) { return null; } }
    private String json(Object value) { if (value == null) return null; try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Address details are invalid."); } }
    private JsonNode jsonNode(String value) { try { return StringUtils.hasText(value) && value.trim().startsWith("{") ? objectMapper.readTree(value) : objectMapper.createObjectNode(); } catch (Exception e) { return objectMapper.createObjectNode(); } }
    private String text(JsonNode node, String... names) { for (String name : names) { String value = node.path(name).asText(""); if (StringUtils.hasText(value)) return value.trim(); } return null; }
    private BigDecimal decimalNode(JsonNode node, String... names) { String value = text(node, names); if (!StringUtils.hasText(value)) return BigDecimal.ZERO; try { return new BigDecimal(value.replaceAll("[^0-9.-]", "")); } catch (Exception e) { return BigDecimal.ZERO; } }
    private Long longNode(JsonNode node, String name) { if (!node.hasNonNull(name)) return null; try { return node.path(name).asLong(); } catch (Exception e) { return null; } }
    private String firstNonBlank(String... values) { for (String value : values) if (StringUtils.hasText(value)) return value.trim(); return null; }
    private String code(SupplyState state) { return state == null ? null : state.getCode(); } private String name(SupplyState state) { return state == null ? "" : state.getName(); }
    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal decimal(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; } private String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String currentUser() { var authentication = SecurityContextHolder.getContext().getAuthentication(); return authentication == null || !authentication.isAuthenticated() ? "System" : authentication.getName(); }
    private String extractStateCode(String value) { if (!StringUtils.hasText(value)) return null; var matcher = java.util.regex.Pattern.compile("(?:\\(|^)(\\d{2})(?:\\)|$)").matcher(value); return matcher.find() ? matcher.group(1) : null; }
    private String stripStateCode(String value) { return value == null ? "" : value.replaceAll("\\s*\\(\\d{2}\\)\\s*$", "").trim(); }
    private record ItemMetadata(String itemType, String description, String hsnCode, String sacCode, String unit, String accountName, BigDecimal purchaseRate, Long taxId) { }
    private record OrganizationProfile(String stateCode, String stateName, String country) { }
}
