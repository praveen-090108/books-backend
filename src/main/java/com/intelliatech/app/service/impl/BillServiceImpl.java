package com.intelliatech.app.service.impl;

import com.intelliatech.app.dto.request.*;
import com.intelliatech.app.dto.response.*;
import com.intelliatech.app.entity.*;
import com.intelliatech.app.exception.*;
import com.intelliatech.app.repository.*;
import com.intelliatech.app.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {
    private static final Long ORGANIZATION_ID = 1L;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final VendorRepository vendorRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final TaxRateRepository taxRateRepository;
    private final SupplyStateRepository supplyStateRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final ExpenseTaxCalculator taxCalculator;
    private final BankAccountService bankAccountService;
    private final DocumentNumberPreferenceService numberService;
    private final BusinessRecordRepository businessRecordRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Page<BillResponse> findAll(String search, Long vendorId, String status, String paymentStatus,
            LocalDate billDateFrom, LocalDate billDateTo, LocalDate dueDateFrom, LocalDate dueDateTo,
            BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {
        refreshOverdue();
        return billRepository.findAll(specification(search, vendorId, status, paymentStatus, billDateFrom,
                billDateTo, dueDateFrom, dueDateTo, minAmount, maxAmount), pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public BillSummaryResponse summary() {
        refreshOverdue();
        List<Bill> bills = billRepository.findAllByOrganizationIdAndDeletedFalse(ORGANIZATION_ID);
        long drafts = 0, open = 0, partial = 0, paid = 0, overdue = 0;
        BigDecimal total = ZERO, paidAmount = ZERO, due = ZERO;
        for (Bill bill : bills) {
            total = total.add(bill.getTotalAmount()); paidAmount = paidAmount.add(bill.getAmountPaid()); due = due.add(bill.getBalanceDue());
            switch (bill.getStatus()) {
                case DRAFT -> drafts++;
                case OPEN -> open++;
                case PARTIALLY_PAID -> partial++;
                case PAID -> paid++;
                case OVERDUE -> overdue++;
                default -> { }
            }
        }
        return new BillSummaryResponse(bills.size(), drafts, open, partial, paid, overdue,
                money(total), money(paidAmount), money(due));
    }

    @Override
    @Transactional(readOnly = true)
    public BillFiltersResponse filters() {
        PurchaseOrderFiltersResponse base = purchaseOrderService.filters();
        List<BillFiltersResponse.PurchaseOrderOption> purchaseOrders = purchaseOrderRepository
                .findAllByOrganizationIdAndDeletedFalse(ORGANIZATION_ID).stream()
                .filter(order -> order.getStatus() != PurchaseOrderStatus.CANCELLED && order.getStatus() != PurchaseOrderStatus.CLOSED)
                .map(order -> new BillFiltersResponse.PurchaseOrderOption(order.getId(), order.getPurchaseOrderNumber(),
                        order.getVendor() == null ? null : order.getVendor().getId(), order.getVendorName(), order.getStatus().name()))
                .toList();
        return new BillFiltersResponse(base.vendors(), base.items(), base.taxes(), base.states(), purchaseOrders,
                Arrays.stream(GstTreatment.values()).map(Enum::name).toList(),
                Arrays.stream(BillStatus.values()).map(Enum::name).toList(), base.organizationStateCode(),
                base.organizationStateName(), base.organizationCountry());
    }

    @Override
    @Transactional
    public BillResponse findById(Long id) {
        Bill bill = getBill(id);
        refreshOverdue(bill);
        return toResponse(bill);
    }

    @Override
    @Transactional
    public BillResponse create(BillRequest request, String action) {
        validateUniqueNumber(request.billNumber(), null);
        Bill bill = new Bill(); bill.setOrganizationId(ORGANIZATION_ID);
        bill.setCreatedBy(currentUser()); bill.setUpdatedBy(currentUser());
        copy(request, bill); bill.setStatus(BillStatus.DRAFT);
        activity(bill, "CREATED", "Bill created as Draft.");
        if (isOpenAction(action)) open(bill, "Bill saved as Open.");
        return toResponse(billRepository.saveAndFlush(bill));
    }

    @Override
    @Transactional
    public BillResponse update(Long id, BillRequest request, String action) {
        Bill bill = getBillForUpdate(id);
        if (!EnumSet.of(BillStatus.DRAFT, BillStatus.OPEN, BillStatus.OVERDUE).contains(bill.getStatus())) {
            throw new ResourceConflictException("Paid, Partially Paid, or Void Bills cannot be edited.");
        }
        if (activePayments(bill).signum() > 0) throw new ResourceConflictException("Reverse recorded payments before editing this Bill.");
        validateUniqueNumber(request.billNumber(), id);
        copy(request, bill); bill.setUpdatedBy(currentUser());
        if (bill.getStatus() == BillStatus.OVERDUE) bill.setStatus(BillStatus.OPEN);
        if (isOpenAction(action)) open(bill, "Bill updated and marked as Open.");
        else bill.setStatus(BillStatus.DRAFT);
        recalculateBalance(bill); activity(bill, "UPDATED", "Bill details updated.");
        return toResponse(billRepository.save(bill));
    }

    @Override
    @Transactional
    public BillResponse convertToOpen(Long id) {
        Bill bill = getBillForUpdate(id);
        if (bill.getStatus() != BillStatus.DRAFT) throw new ResourceConflictException("Only Draft Bills can be converted to Open.");
        validateOpen(bill); open(bill, "Bill converted from Draft to Open.");
        return toResponse(bill);
    }

    @Override
    @Transactional
    public BillResponse recordPayment(Long id, BillPaymentRequest request, String idempotencyKey) {
        Bill bill = getBillForUpdate(id); refreshOverdue(bill);
        String requestKey = clean(idempotencyKey);
        if (requestKey != null) {
            BillPayment existing = billPaymentRepository.findByIdempotencyKey(requestKey).orElse(null);
            if (existing != null) {
                if (!existing.getBill().getId().equals(bill.getId())) {
                    throw new ResourceConflictException("This payment request has already been used for another Bill.");
                }
                return toResponse(bill);
            }
        }
        if (!EnumSet.of(BillStatus.OPEN, BillStatus.PARTIALLY_PAID, BillStatus.OVERDUE).contains(bill.getStatus())) {
            throw new ResourceConflictException("Payments can only be recorded for Open, Partially Paid, or Overdue Bills.");
        }
        BigDecimal amount = money(request.amount());
        if (amount.compareTo(bill.getBalanceDue()) > 0) throw new IllegalArgumentException("Payment amount cannot exceed the current Balance Due.");
        BillPayment payment = new BillPayment(); payment.setIdempotencyKey(requestKey);
        payment.setPaymentNumber(nextPaymentNumber()); payment.setPaymentDate(request.paymentDate());
        payment.setPaymentMode(clean(request.paymentMode()));
        applyBankAccount(payment, request);
        payment.setToAccount(clean(request.toAccount())); payment.setReferenceNumber(clean(request.referenceNumber()));
        payment.setAmount(amount); payment.setNotes(clean(request.notes())); payment.setAttachmentName(clean(request.attachmentName()));
        payment.setAttachmentUrl(clean(request.attachmentUrl())); payment.setCreatedBy(currentUser());
        bill.addPayment(payment);
        // Bill is already managed here. Persist the new child explicitly before
        // flushing the orphan-removal collection so Hibernate can compare it safely.
        billPaymentRepository.save(payment);
        recalculateBalance(bill); bill.setUpdatedBy(currentUser());
        activity(bill, "PAYMENT_RECORDED", payment.getPaymentNumber() + " recorded for " + amount.toPlainString() + ".");
        return toResponse(billRepository.saveAndFlush(bill));
    }

    @Override
    @Transactional
    public BillResponse updatePayment(Long id, Long paymentId, BillPaymentRequest request) {
        Bill bill = getBillForUpdate(id);
        BillPayment payment = bill.getPayments().stream().filter(item -> item.getId().equals(paymentId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for this Bill."));
        if (payment.getStatus() == BillPaymentStatus.REVERSED) {
            throw new ResourceConflictException("Reversed payments cannot be edited.");
        }
        BigDecimal maximum = money(bill.getBalanceDue().add(payment.getAmount()));
        BigDecimal amount = money(request.amount());
        if (amount.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed the Bill's eligible Balance Due.");
        }
        payment.setPaymentDate(request.paymentDate()); payment.setPaymentMode(clean(request.paymentMode()));
        applyBankAccount(payment, request); payment.setToAccount(clean(request.toAccount()));
        payment.setReferenceNumber(clean(request.referenceNumber())); payment.setAmount(amount);
        payment.setNotes(clean(request.notes())); payment.setAttachmentName(clean(request.attachmentName()));
        payment.setAttachmentUrl(clean(request.attachmentUrl())); recalculateBalance(bill); bill.setUpdatedBy(currentUser());
        activity(bill, "PAYMENT_UPDATED", payment.getPaymentNumber() + " updated to " + amount.toPlainString() + ".");
        return toResponse(billRepository.saveAndFlush(bill));
    }

    @Override
    @Transactional
    public BillResponse reversePayment(Long id, Long paymentId, BillActionRequest request) {
        Bill bill = getBillForUpdate(id);
        BillPayment payment = bill.getPayments().stream().filter(item -> item.getId().equals(paymentId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for this Bill."));
        if (payment.getStatus() == BillPaymentStatus.REVERSED) throw new ResourceConflictException("This payment has already been reversed.");
        payment.setStatus(BillPaymentStatus.REVERSED); payment.setReversedAt(LocalDateTime.now()); payment.setReversedBy(currentUser());
        payment.setReversalReason(clean(request.reason())); recalculateBalance(bill); bill.setUpdatedBy(currentUser());
        activity(bill, "PAYMENT_REVERSED", payment.getPaymentNumber() + (StringUtils.hasText(request.reason()) ? ": " + request.reason().trim() : "."));
        return toResponse(bill);
    }

    @Override
    @Transactional
    public BillResponse cloneBill(Long id) {
        Bill source = getBill(id); Bill clone = new Bill();
        clone.setOrganizationId(ORGANIZATION_ID); clone.setBillNumber(nextBillNumber());
        clone.setVendor(source.getVendor()); clone.setVendorName(source.getVendorName()); clone.setBillDate(LocalDate.now());
        clone.setDueDate(LocalDate.now().plusDays(daysForTerms(source.getPaymentTerms()))); clone.setPaymentTerms(source.getPaymentTerms());
        clone.setPlaceOfSupply(source.getPlaceOfSupply()); clone.setSourceOfSupply(source.getSourceOfSupply());
        clone.setDestinationOfSupply(source.getDestinationOfSupply()); clone.setGstTreatment(source.getGstTreatment());
        clone.setCurrencyCode(source.getCurrencyCode()); clone.setExchangeRate(source.getExchangeRate()); clone.setSubject(source.getSubject());
        clone.setAmountType(source.getAmountType()); clone.setShippingCharge(source.getShippingCharge()); clone.setAdjustmentAmount(source.getAdjustmentAmount());
        clone.setNotes(source.getNotes()); clone.setTermsAndConditions(source.getTermsAndConditions()); clone.setCreatedBy(currentUser()); clone.setUpdatedBy(currentUser());
        clone.replaceItems(source.getItems().stream().map(this::cloneItem).toList()); recalculate(clone); recalculateBalance(clone);
        activity(clone, "CLONED", "Cloned from " + source.getBillNumber() + ".");
        return toResponse(billRepository.saveAndFlush(clone));
    }

    @Override
    @Transactional
    public BillResponse voidBill(Long id, BillActionRequest request) {
        Bill bill = getBillForUpdate(id);
        if (bill.getStatus() == BillStatus.PAID || activePayments(bill).signum() > 0) {
            throw new ResourceConflictException("Reverse all payments before voiding this Bill.");
        }
        if (bill.getStatus() == BillStatus.VOID) throw new ResourceConflictException("This Bill is already Void.");
        bill.setStatus(BillStatus.VOID); bill.setVoidedAt(LocalDateTime.now()); bill.setVoidedBy(currentUser());
        bill.setVoidReason(clean(request.reason())); bill.setUpdatedBy(currentUser());
        activity(bill, "VOIDED", StringUtils.hasText(request.reason()) ? request.reason().trim() : "Bill voided.");
        return toResponse(bill);
    }

    @Override
    @Transactional
    public BillResponse expectedPaymentDate(Long id, BillActionRequest request) {
        Bill bill = getBillForUpdate(id);
        if (request.expectedPaymentDate() == null) throw new IllegalArgumentException("Expected Payment Date is required.");
        bill.setExpectedPaymentDate(request.expectedPaymentDate()); bill.setUpdatedBy(currentUser());
        activity(bill, "EXPECTED_PAYMENT_DATE", "Expected payment on " + request.expectedPaymentDate() + ".");
        return toResponse(bill);
    }

    @Override @Transactional(readOnly = true) public byte[] pdf(Long id) { return buildPdf(getBill(id)); }

    @Override
    @Transactional(readOnly = true)
    public byte[] paymentReceiptPdf(Long id, Long paymentId) {
        Bill bill = getBill(id);
        BillPayment payment = bill.getPayments().stream()
                .filter(item -> item.getId().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for this Bill."));
        if (payment.getStatus() == BillPaymentStatus.REVERSED) {
            throw new ResourceConflictException("A receipt cannot be generated for a reversed payment.");
        }
        return buildPaymentReceiptPdf(bill, payment);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Bill bill = getBillForUpdate(id);
        if (bill.getStatus() == BillStatus.PAID || bill.getStatus() == BillStatus.PARTIALLY_PAID || activePayments(bill).signum() > 0) {
            throw new ResourceConflictException("A Bill with payment history cannot be deleted. Reverse payments and void it instead.");
        }
        if (!EnumSet.of(BillStatus.DRAFT, BillStatus.OPEN, BillStatus.OVERDUE).contains(bill.getStatus())) {
            throw new ResourceConflictException("This Bill cannot be deleted in its current status.");
        }
        bill.setDeleted(true); bill.setUpdatedBy(currentUser()); activity(bill, "DELETED", "Bill deleted.");
    }

    private void copy(BillRequest request, Bill bill) {
        Vendor vendor = vendorRepository.findByIdAndOrganizationId(request.vendorId(), ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found."));
        if (vendor.getStatus() != VendorStatus.ACTIVE) throw new IllegalArgumentException("Only active Vendors can be selected.");
        if (request.dueDate().isBefore(request.billDate())) throw new IllegalArgumentException("Due Date cannot be before Bill Date.");
        SupplyState place = state(request.placeOfSupplyCode(), "Place of Supply");
        SupplyState source = state(request.sourceOfSupplyCode(), "Source of Supply");
        SupplyState destination = state(request.destinationOfSupplyCode(), "Destination of Supply");
        PurchaseOrder order = null;
        if (request.purchaseOrderId() != null) {
            order = purchaseOrderRepository.findByIdAndOrganizationIdAndDeletedFalse(request.purchaseOrderId(), ORGANIZATION_ID)
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found."));
            if (order.getVendor() != null && !order.getVendor().getId().equals(vendor.getId())) {
                throw new IllegalArgumentException("The selected Purchase Order does not belong to this Vendor.");
            }
        }
        bill.setBillNumber(request.billNumber().trim()); bill.setVendor(vendor); bill.setVendorName(vendor.getVendorName());
        bill.setBillDate(request.billDate()); bill.setDueDate(request.dueDate()); bill.setReferenceNumber(clean(request.referenceNumber()));
        bill.setPurchaseOrder(order); bill.setPurchaseOrderNumber(order == null ? null : order.getPurchaseOrderNumber());
        bill.setPaymentTerms(clean(request.paymentTerms())); bill.setPlaceOfSupply(place); bill.setSourceOfSupply(source); bill.setDestinationOfSupply(destination);
        bill.setGstTreatment(request.gstTreatment()); bill.setCurrencyCode(request.currencyCode().trim().toUpperCase(Locale.ROOT));
        bill.setExchangeRate(request.exchangeRate()); bill.setSubject(clean(request.subject())); bill.setAmountType(request.amountType());
        bill.setShippingCharge(money(request.shippingCharge())); bill.setAdjustmentAmount(money(request.adjustmentAmount()));
        bill.setNotes(clean(request.notes())); bill.setTermsAndConditions(clean(request.termsAndConditions()));
        bill.setAttachmentName(clean(request.attachmentName())); bill.setAttachmentUrl(clean(request.attachmentUrl()));
        Map<Long, PurchaseOrderFiltersResponse.ItemOption> masters = filters().items().stream()
                .collect(Collectors.toMap(PurchaseOrderFiltersResponse.ItemOption::id, Function.identity()));
        List<BillItem> items = new ArrayList<>();
        for (int index = 0; index < request.items().size(); index++) items.add(toItem(request.items().get(index), bill, index, masters));
        bill.replaceItems(items); recalculate(bill); recalculateBalance(bill);
    }

    private BillItem toItem(BillItemRequest request, Bill bill, int index, Map<Long, PurchaseOrderFiltersResponse.ItemOption> masters) {
        PurchaseOrderFiltersResponse.ItemOption master = masters.get(request.itemId());
        if (master == null) throw new ResourceNotFoundException("Item not found or inactive.");
        TaxRate tax = request.taxId() == null ? null : taxRateRepository.findById(request.taxId()).filter(TaxRate::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Tax rate not found."));
        PurchaseOrderDiscountType type = request.discountType() == null ? PurchaseOrderDiscountType.NONE : request.discountType();
        BigDecimal discountValue = decimal(request.discountValue()); BigDecimal gross = request.quantity().multiply(request.rate());
        BigDecimal discount = switch (type) {
            case NONE -> ZERO;
            case PERCENTAGE -> {
                if (discountValue.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("Percentage discount cannot exceed 100.");
                yield gross.multiply(discountValue).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            }
            case FLAT_AMOUNT -> discountValue;
        };
        if (discount.compareTo(gross) > 0) throw new IllegalArgumentException("Discount cannot exceed the item amount for " + master.name() + ".");
        BigDecimal discounted = gross.subtract(discount);
        ExpenseTaxSummaryResponse summary = taxCalculator.calculate(discounted, bill.getAmountType(), bill.getGstTreatment(),
                bill.getSourceOfSupply().getCode(), bill.getDestinationOfSupply().getCode(), tax);
        BillItem item = new BillItem(); item.setItemId(master.id()); item.setItemName(master.name()); item.setItemSku(master.sku());
        item.setItemType(master.itemType()); item.setDescription(firstNonBlank(request.description(), master.description()));
        item.setAccountName(firstNonBlank(request.accountName(), master.accountName(), "Purchases"));
        item.setHsnCode(firstNonBlank(request.hsnCode(), master.hsnCode())); item.setSacCode(firstNonBlank(request.sacCode(), master.sacCode()));
        item.setQuantity(request.quantity()); item.setUnit(firstNonBlank(request.unit(), master.unit(), "Nos")); item.setRate(request.rate());
        item.setDiscountType(type); item.setDiscountValue(discountValue); item.setDiscountAmount(money(discount)); item.setTax(tax);
        item.setTaxName(tax == null ? null : tax.getName()); item.setTaxRate(tax == null ? BigDecimal.ZERO : tax.getRate());
        item.setTaxableAmount(summary.taxableAmount()); item.setCgstAmount(summary.cgstAmount()); item.setSgstAmount(summary.sgstAmount());
        item.setIgstAmount(summary.igstAmount()); item.setCessAmount(summary.cessAmount()); item.setLineTotal(summary.totalAmount()); item.setSortOrder(index);
        return item;
    }

    private void recalculate(Bill bill) {
        BigDecimal subtotal = ZERO, discount = ZERO, taxable = ZERO, cgst = ZERO, sgst = ZERO, igst = ZERO, cess = ZERO;
        for (BillItem item : bill.getItems()) {
            subtotal = subtotal.add(item.getQuantity().multiply(item.getRate())); discount = discount.add(item.getDiscountAmount());
            taxable = taxable.add(item.getTaxableAmount()); cgst = cgst.add(item.getCgstAmount()); sgst = sgst.add(item.getSgstAmount());
            igst = igst.add(item.getIgstAmount()); cess = cess.add(item.getCessAmount());
        }
        BigDecimal tax = cgst.add(sgst).add(igst).add(cess);
        bill.setSubtotal(money(subtotal)); bill.setDiscountAmount(money(discount)); bill.setTaxableAmount(money(taxable));
        bill.setCgstAmount(money(cgst)); bill.setSgstAmount(money(sgst)); bill.setIgstAmount(money(igst)); bill.setCessAmount(money(cess));
        bill.setTotalTaxAmount(money(tax)); bill.setRoundOffAmount(ZERO);
        bill.setTotalAmount(money(taxable.add(tax).add(bill.getShippingCharge()).add(bill.getAdjustmentAmount())));
    }

    private void recalculateBalance(Bill bill) {
        BigDecimal paid = activePayments(bill); bill.setAmountPaid(paid);
        bill.setBalanceDue(money(bill.getTotalAmount().subtract(paid).max(BigDecimal.ZERO)));
        if (bill.getStatus() == BillStatus.VOID || bill.getStatus() == BillStatus.DRAFT) return;
        if (bill.getBalanceDue().signum() == 0) { bill.setStatus(BillStatus.PAID); if (bill.getPaidAt() == null) bill.setPaidAt(LocalDateTime.now()); }
        else if (paid.signum() > 0) { bill.setStatus(BillStatus.PARTIALLY_PAID); bill.setPaidAt(null); }
        else { bill.setStatus(bill.getDueDate().isBefore(LocalDate.now()) ? BillStatus.OVERDUE : BillStatus.OPEN); bill.setPaidAt(null); }
    }

    private BigDecimal activePayments(Bill bill) { return money(bill.getPayments().stream()
            .filter(payment -> payment.getStatus() == BillPaymentStatus.PAID).map(BillPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)); }

    private BillResponse toResponse(Bill bill) {
        Vendor vendor = bill.getVendor();
        return new BillResponse(bill.getId(), bill.getBillNumber(), vendor == null ? null : vendor.getId(), bill.getVendorName(),
                vendor == null ? null : vendor.getGstin(), vendor == null ? null : vendor.getEmail(), vendor == null ? null : vendor.getPhone(),
                vendor == null ? null : vendorAddress(vendor), bill.getBillDate(), bill.getDueDate(), bill.getReferenceNumber(),
                bill.getPurchaseOrder() == null ? null : bill.getPurchaseOrder().getId(), bill.getPurchaseOrderNumber(), bill.getPaymentTerms(),
                code(bill.getPlaceOfSupply()), name(bill.getPlaceOfSupply()), code(bill.getSourceOfSupply()), name(bill.getSourceOfSupply()),
                code(bill.getDestinationOfSupply()), name(bill.getDestinationOfSupply()), bill.getGstTreatment(), bill.getCurrencyCode(), bill.getExchangeRate(),
                bill.getSubject(), bill.getAmountType(), bill.getStatus(), bill.getSubtotal(), bill.getDiscountAmount(), bill.getTaxableAmount(),
                bill.getCgstAmount(), bill.getSgstAmount(), bill.getIgstAmount(), bill.getCessAmount(), bill.getShippingCharge(),
                bill.getAdjustmentAmount(), bill.getRoundOffAmount(), bill.getTotalTaxAmount(), bill.getTotalAmount(), bill.getAmountPaid(),
                bill.getBalanceDue(), amountInWords(bill.getTotalAmount()), bill.getNotes(), bill.getTermsAndConditions(), bill.getAttachmentName(),
                bill.getAttachmentUrl(), bill.getExpectedPaymentDate(), bill.getOpenedAt(), bill.getPaidAt(), bill.getVoidedAt(), bill.getVoidReason(),
                bill.getCreatedBy(), bill.getUpdatedBy(), bill.getCreatedAt(), bill.getUpdatedAt(),
                bill.getItems().stream().map(this::itemResponse).toList(), bill.getPayments().stream().map(this::paymentResponse).toList(),
                bill.getActivities().stream().map(activity -> new BillActivityResponse(activity.getId(), activity.getAction(), activity.getDetails(),
                        activity.getPerformedBy(), activity.getCreatedAt())).toList(), actions(bill));
    }

    private BillItemResponse itemResponse(BillItem item) { return new BillItemResponse(item.getId(), item.getItemId(), item.getItemName(),
            item.getItemSku(), item.getItemType(), item.getDescription(), item.getAccountName(), item.getHsnCode(), item.getSacCode(), item.getQuantity(),
            item.getUnit(), item.getRate(), item.getDiscountType(), item.getDiscountValue(), item.getDiscountAmount(),
            item.getTax() == null ? null : item.getTax().getId(), item.getTaxName(), item.getTaxRate(), item.getTaxableAmount(), item.getCgstAmount(),
            item.getSgstAmount(), item.getIgstAmount(), item.getCessAmount(), item.getLineTotal()); }

    private BillPaymentResponse paymentResponse(BillPayment payment) { return new BillPaymentResponse(payment.getId(), payment.getPaymentNumber(),
            payment.getPaymentDate(), payment.getPaymentMode(), payment.getPaidThrough(), payment.getBankAccount() == null ? null : payment.getBankAccount().getId(), payment.getToAccount(), payment.getReferenceNumber(),
            payment.getAmount(), payment.getNotes(), payment.getAttachmentName(), payment.getAttachmentUrl(), payment.getStatus(), payment.getReversedAt(),
            payment.getReversalReason(), payment.getCreatedBy(), payment.getCreatedAt()); }

    private void applyBankAccount(BillPayment payment, BillPaymentRequest request) {
        Long retainedId = payment.getBankAccount() == null ? null : payment.getBankAccount().getId();
        BankAccountMaster account = bankAccountService.selection(request.bankAccountId(), retainedId);
        if (bankAccountService.requiresAccount(request.paymentMode()) && account == null && !StringUtils.hasText(request.paidThrough())) {
            throw new IllegalArgumentException("Bank Account is required for the selected Payment Mode.");
        }
        payment.setBankAccount(account);
        payment.setPaidThrough(account == null ? Objects.requireNonNullElse(clean(request.paidThrough()), "Cash") : account.getAccountName());
    }

    private Specification<Bill> specification(String search, Long vendorId, String status, String paymentStatus,
            LocalDate billDateFrom, LocalDate billDateTo, LocalDate dueDateFrom, LocalDate dueDateTo,
            BigDecimal minAmount, BigDecimal maxAmount) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), ORGANIZATION_ID)); predicates.add(builder.isFalse(root.get("deleted")));
            if (StringUtils.hasText(search)) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(builder.like(builder.lower(root.get("billNumber")), term),
                        builder.like(builder.lower(root.get("vendorName")), term), builder.like(builder.lower(root.get("referenceNumber")), term),
                        builder.like(builder.lower(root.get("subject")), term)));
            }
            if (vendorId != null) predicates.add(builder.equal(root.get("vendor").get("id"), vendorId));
            if (StringUtils.hasText(status)) predicates.add(builder.equal(root.get("status"), parseStatus(status)));
            if (StringUtils.hasText(paymentStatus)) {
                switch (paymentStatus.trim().toUpperCase(Locale.ROOT)) {
                    case "PAID" -> predicates.add(builder.equal(root.get("balanceDue"), BigDecimal.ZERO));
                    case "PARTIAL" -> { predicates.add(builder.greaterThan(root.get("amountPaid"), BigDecimal.ZERO)); predicates.add(builder.greaterThan(root.get("balanceDue"), BigDecimal.ZERO)); }
                    case "UNPAID" -> predicates.add(builder.equal(root.get("amountPaid"), BigDecimal.ZERO));
                    default -> throw new IllegalArgumentException("Unsupported payment status.");
                }
            }
            if (billDateFrom != null) predicates.add(builder.greaterThanOrEqualTo(root.get("billDate"), billDateFrom));
            if (billDateTo != null) predicates.add(builder.lessThanOrEqualTo(root.get("billDate"), billDateTo));
            if (dueDateFrom != null) predicates.add(builder.greaterThanOrEqualTo(root.get("dueDate"), dueDateFrom));
            if (dueDateTo != null) predicates.add(builder.lessThanOrEqualTo(root.get("dueDate"), dueDateTo));
            if (minAmount != null) predicates.add(builder.greaterThanOrEqualTo(root.get("totalAmount"), minAmount));
            if (maxAmount != null) predicates.add(builder.lessThanOrEqualTo(root.get("totalAmount"), maxAmount));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Set<String> actions(Bill bill) {
        Set<String> actions = new LinkedHashSet<>(List.of("VIEW", "PDF", "PRINT", "CLONE"));
        switch (bill.getStatus()) {
            case DRAFT -> actions.addAll(List.of("EDIT", "OPEN", "DELETE"));
            case OPEN, OVERDUE -> actions.addAll(List.of("EDIT", "PAY", "VOID", "EXPECTED_PAYMENT_DATE", "DELETE"));
            case PARTIALLY_PAID -> actions.addAll(List.of("PAY", "EXPECTED_PAYMENT_DATE"));
            case PAID -> { }
            case VOID -> actions.remove("CLONE");
        }
        if (bill.getPayments().stream().anyMatch(payment -> payment.getStatus() == BillPaymentStatus.PAID)) actions.add("PAYMENT_HISTORY");
        return actions;
    }

    private void open(Bill bill, String details) { validateOpen(bill); bill.setStatus(BillStatus.OPEN);
        if (bill.getOpenedAt() == null) bill.setOpenedAt(LocalDateTime.now()); if (!StringUtils.hasText(bill.getOpenedBy())) bill.setOpenedBy(currentUser());
        recalculateBalance(bill); activity(bill, "OPENED", details); }
    private void validateOpen(Bill bill) { if (bill.getVendor() == null) throw new IllegalArgumentException("Vendor is required.");
        if (!StringUtils.hasText(bill.getBillNumber())) throw new IllegalArgumentException("Bill number is required.");
        if (bill.getItems().isEmpty()) throw new IllegalArgumentException("At least one valid item is required.");
        if (bill.getTotalAmount().signum() <= 0) throw new IllegalArgumentException("Bill total must be greater than zero."); }

    private void refreshOverdue() { billRepository.findAllByOrganizationIdAndDeletedFalse(ORGANIZATION_ID).forEach(this::refreshOverdue); }
    private void refreshOverdue(Bill bill) { if (bill.getDueDate() != null && bill.getDueDate().isBefore(LocalDate.now()) && bill.getBalanceDue().signum() > 0
            && EnumSet.of(BillStatus.OPEN, BillStatus.PARTIALLY_PAID).contains(bill.getStatus())) bill.setStatus(BillStatus.OVERDUE); }

    private Bill getBill(Long id) { return billRepository.findByIdAndOrganizationIdAndDeletedFalse(id, ORGANIZATION_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Bill not found.")); }
    private Bill getBillForUpdate(Long id) { return billRepository.findForUpdate(id, ORGANIZATION_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Bill not found.")); }
    private SupplyState state(String code, String label) { return supplyStateRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException(label + " is not valid.")); }
    private BillStatus parseStatus(String status) { try { return BillStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception exception) { throw new IllegalArgumentException("Unsupported Bill status."); } }
    private void validateUniqueNumber(String number, Long id) { boolean exists = id == null
            ? billRepository.existsByOrganizationIdAndBillNumberIgnoreCaseAndDeletedFalse(ORGANIZATION_ID, number.trim())
            : billRepository.existsByOrganizationIdAndBillNumberIgnoreCaseAndDeletedFalseAndIdNot(ORGANIZATION_ID, number.trim(), id);
        if (exists) throw new DuplicateResourceException("A Bill with this number already exists."); }
    private void activity(Bill bill, String action, String details) { BillActivity activity = new BillActivity(); activity.setAction(action);
        activity.setDetails(details); activity.setPerformedBy(currentUser()); bill.addActivity(activity); }
    private boolean isOpenAction(String action) { return "open".equalsIgnoreCase(action) || "save-open".equalsIgnoreCase(action); }

    private String nextPaymentNumber() { String number; do { number = numberService.allocateForCreate("billPayments"); }
        while (billPaymentRepository.existsByPaymentNumber(number)); return number; }
    private String nextBillNumber() { String number; do { number = numberService.allocateForCreate("bills"); }
        while (billRepository.existsByOrganizationIdAndBillNumberIgnoreCaseAndDeletedFalse(ORGANIZATION_ID, number)); return number; }
    private int daysForTerms(String terms) { if (!StringUtils.hasText(terms)) return 30; var matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(terms);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 30; }

    private BillItem cloneItem(BillItem source) { BillItem item = new BillItem(); item.setItemId(source.getItemId()); item.setItemName(source.getItemName());
        item.setItemSku(source.getItemSku()); item.setItemType(source.getItemType()); item.setDescription(source.getDescription()); item.setAccountName(source.getAccountName());
        item.setHsnCode(source.getHsnCode()); item.setSacCode(source.getSacCode()); item.setQuantity(source.getQuantity()); item.setUnit(source.getUnit()); item.setRate(source.getRate());
        item.setDiscountType(source.getDiscountType()); item.setDiscountValue(source.getDiscountValue()); item.setDiscountAmount(source.getDiscountAmount()); item.setTax(source.getTax());
        item.setTaxName(source.getTaxName()); item.setTaxRate(source.getTaxRate()); item.setTaxableAmount(source.getTaxableAmount()); item.setCgstAmount(source.getCgstAmount());
        item.setSgstAmount(source.getSgstAmount()); item.setIgstAmount(source.getIgstAmount()); item.setCessAmount(source.getCessAmount()); item.setLineTotal(source.getLineTotal());
        item.setSortOrder(source.getSortOrder()); return item; }

    private PurchaseOrderAddressResponse vendorAddress(Vendor vendor) { return new PurchaseOrderAddressResponse(vendor.getPrimaryContact(), vendor.getBillingAddressLine1(),
            vendor.getBillingAddressLine2(), vendor.getBillingCity(), vendor.getBillingState(), extractStateCode(vendor.getSourceOfSupply()), vendor.getBillingCountry(),
            vendor.getBillingPincode(), vendor.getPhone(), vendor.getEmail(), vendor.getGstin()); }

    private byte[] buildPdf(Bill bill) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 46, 46, 40, 44);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new BillPageFooter());
            document.open();

            Color ink = new Color(48, 48, 48);
            Color muted = new Color(82, 82, 82);
            Color charcoal = new Color(56, 58, 54);
            Font body = billFont(9.3f, false, ink);
            Font bodyBold = billFont(9.3f, true, ink);
            Font small = billFont(8.3f, false, muted);
            Font label = billFont(9.6f, true, ink);
            Font documentTitle = billFont(29, true, new Color(18, 18, 18));
            BillCompanyProfile profile = billCompanyProfile();

            PdfPTable header = new PdfPTable(new float[]{3.05f, 2f});
            header.setWidthPercentage(100);
            header.setSpacingAfter(35);
            PdfPCell companyCell = borderlessBillCell();
            Image logo = billLogo(profile.logoUrl());
            if (logo != null) {
                logo.scaleToFit(188, 72);
                logo.setAlignment(Image.ALIGN_LEFT);
                companyCell.addElement(logo);
            } else {
                Paragraph wordmark = new Paragraph("INTELLIATECH", billFont(18, true, new Color(32, 32, 32)));
                wordmark.setSpacingAfter(11);
                companyCell.addElement(wordmark);
            }
            Paragraph companyName = new Paragraph(profile.name(), billFont(10.4f, true, ink));
            companyName.setSpacingBefore(7);
            companyName.setSpacingAfter(3);
            companyCell.addElement(companyName);
            Paragraph companyDetails = new Paragraph(companyDetailsText(profile), body);
            companyDetails.setLeading(13.2f);
            companyCell.addElement(companyDetails);
            header.addCell(companyCell);

            PdfPCell titleCell = borderlessBillCell();
            titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph title = new Paragraph("BILL", documentTitle);
            title.setAlignment(Element.ALIGN_RIGHT);
            title.setSpacingAfter(7);
            titleCell.addElement(title);
            Paragraph billNumber = new Paragraph("Bill#  " + bill.getBillNumber(), bodyBold);
            billNumber.setAlignment(Element.ALIGN_RIGHT);
            billNumber.setSpacingAfter(22);
            titleCell.addElement(billNumber);
            Paragraph balanceLabel = new Paragraph("Balance Due", body);
            balanceLabel.setAlignment(Element.ALIGN_RIGHT);
            titleCell.addElement(balanceLabel);
            Paragraph balance = new Paragraph(currency(bill.getBalanceDue(), bill.getCurrencyCode()), billCurrencyFont(16, true, ink));
            balance.setAlignment(Element.ALIGN_RIGHT);
            titleCell.addElement(balance);
            header.addCell(titleCell);
            document.add(header);

            PdfPTable partyAndMeta = new PdfPTable(new float[]{1.42f, 1f});
            partyAndMeta.setWidthPercentage(100);
            partyAndMeta.setSpacingAfter(27);
            PdfPCell vendorCell = borderlessBillCell();
            Paragraph fromLabel = new Paragraph("Bill From", label);
            fromLabel.setSpacingAfter(5);
            vendorCell.addElement(fromLabel);
            Paragraph vendorName = new Paragraph(bill.getVendorName(), bodyBold);
            vendorName.setSpacingAfter(2);
            vendorCell.addElement(vendorName);
            Paragraph vendorAddress = new Paragraph(vendorAddressLines(bill.getVendor()), body);
            vendorAddress.setLeading(13.2f);
            vendorCell.addElement(vendorAddress);
            partyAndMeta.addCell(vendorCell);

            PdfPTable meta = new PdfPTable(new float[]{1, 1.18f});
            meta.setWidthPercentage(100);
            addBillMetaRow(meta, "Bill Date", formatBillDate(bill.getBillDate()), body, bodyBold);
            addBillMetaRow(meta, "Due Date", formatBillDate(bill.getDueDate()), body, bodyBold);
            addBillMetaRow(meta, "Terms", value(bill.getPaymentTerms()), body, bodyBold);
            if (StringUtils.hasText(bill.getReferenceNumber())) {
                addBillMetaRow(meta, "Reference#", bill.getReferenceNumber(), body, bodyBold);
            }
            PdfPCell metaContainer = borderlessBillCell();
            metaContainer.addElement(meta);
            partyAndMeta.addCell(metaContainer);
            document.add(partyAndMeta);

            if (StringUtils.hasText(bill.getSubject())) {
                PdfPTable subject = new PdfPTable(1);
                subject.setWidthPercentage(100);
                subject.setSpacingAfter(17);
                PdfPCell subjectCell = new PdfPCell();
                subjectCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
                subjectCell.setBorderColor(new Color(210, 210, 210));
                subjectCell.setPadding(8);
                subjectCell.addElement(new Paragraph("Subject", bodyBold));
                subjectCell.addElement(new Paragraph(bill.getSubject(), body));
                subject.addCell(subjectCell);
                document.add(subject);
            }

            PdfPTable items = new PdfPTable(new float[]{0.42f, 4.15f, 1.02f, 0.83f, 1.08f, 1.18f});
            items.setWidthPercentage(100);
            items.setHeaderRows(1);
            addBillItemHeader(items, List.of("#", "Item & Description", "HSN/SAC", "Qty", "Rate", "Amount"), charcoal);
            int index = 1;
            for (BillItem item : bill.getItems()) {
                addBillItemRow(items, List.of(
                        String.valueOf(index++),
                        item.getItemName() + (StringUtils.hasText(item.getDescription()) ? "\n" + item.getDescription() : ""),
                        value(firstNonBlank(item.getHsnCode(), item.getSacCode())),
                        decimal(item.getQuantity()).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        decimalText(item.getRate()),
                        decimalText(item.getTaxableAmount())
                ), body);
            }
            document.add(items);

            PdfPTable settlement = new PdfPTable(new float[]{1.05f, 1f});
            settlement.setWidthPercentage(100);
            settlement.setSpacingBefore(9);
            settlement.setKeepTogether(true);
            PdfPCell signature = borderlessBillCell();
            Paragraph signatureSpace = new Paragraph("\n\n\n\n____________________________\nAuthorized Signature", body);
            signatureSpace.setLeading(15);
            signature.addElement(signatureSpace);
            settlement.addCell(signature);

            PdfPTable totals = new PdfPTable(new float[]{1.55f, 1f});
            totals.setWidthPercentage(100);
            addBillTotal(totals, "Sub Total", bill.getSubtotal(), bill.getCurrencyCode(), body, false, false);
            if (bill.getDiscountAmount().signum() > 0) addBillTotal(totals, "Discount", bill.getDiscountAmount().negate(), bill.getCurrencyCode(), body, false, false);
            if (bill.getCgstAmount().signum() > 0) addBillTotal(totals, taxLabel("CGST", bill), bill.getCgstAmount(), bill.getCurrencyCode(), body, false, false);
            if (bill.getSgstAmount().signum() > 0) addBillTotal(totals, taxLabel("SGST", bill), bill.getSgstAmount(), bill.getCurrencyCode(), body, false, false);
            if (bill.getIgstAmount().signum() > 0) addBillTotal(totals, taxLabel("IGST", bill), bill.getIgstAmount(), bill.getCurrencyCode(), body, false, false);
            if (bill.getCessAmount().signum() > 0) addBillTotal(totals, "Cess", bill.getCessAmount(), bill.getCurrencyCode(), body, false, false);
            if (bill.getShippingCharge().signum() != 0) addBillTotal(totals, "Shipping Charges", bill.getShippingCharge(), bill.getCurrencyCode(), body, false, false);
            if (bill.getAdjustmentAmount().signum() != 0) addBillTotal(totals, "Adjustment", bill.getAdjustmentAmount(), bill.getCurrencyCode(), body, false, false);
            addBillTotal(totals, "Total", bill.getTotalAmount(), bill.getCurrencyCode(), bodyBold, false, false);
            if (bill.getAmountPaid().signum() > 0) addBillTotal(totals, "Payments Made", bill.getAmountPaid(), bill.getCurrencyCode(), body, true, false);
            addBillTotal(totals, "Balance Due", bill.getBalanceDue(), bill.getCurrencyCode(), billFont(10.4f, true, ink), false, true);
            PdfPCell totalsCell = borderlessBillCell();
            totalsCell.addElement(totals);
            settlement.addCell(totalsCell);
            document.add(settlement);

            addBillNotes(document, bill, body, bodyBold);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Bill PDF could not be generated.", exception);
        }
    }

    private void addBillItemHeader(PdfPTable table, List<String> values, Color background) {
        Font font = billFont(8.8f, true, Color.WHITE);
        for (int index = 0; index < values.size(); index++) {
            PdfPCell cell = new PdfPCell(new Phrase(values.get(index), font));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setBackgroundColor(background);
            cell.setPadding(8);
            if (index >= 2) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cell);
        }
    }

    private void addBillItemRow(PdfPTable table, List<String> values, Font font) {
        for (int index = 0; index < values.size(); index++) {
            PdfPCell cell = new PdfPCell(new Phrase(values.get(index), font));
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(new Color(215, 215, 215));
            cell.setPadding(8);
            cell.setVerticalAlignment(Element.ALIGN_TOP);
            if (index >= 2) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cell);
        }
    }

    private void addBillMetaRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = borderlessBillCell(new Phrase(label + "  :", labelFont), 4);
        PdfPCell valueCell = borderlessBillCell(new Phrase(value, valueFont), 4);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addBillTotal(PdfPTable table, String label, BigDecimal amount, String currencyCode,
            Font font, boolean negative, boolean highlighted) {
        Color color = negative ? new Color(219, 37, 37) : font.getColor();
        boolean bold = isBillFontBold(font);
        Font labelFont = billFont(font.getSize(), bold, color);
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5.5f);
        if (highlighted) labelCell.setBackgroundColor(new Color(238, 238, 238));
        table.addCell(labelCell);

        String formatted = negative
                ? "(-) " + amountWithoutSymbol(amount)
                : currency(amount, currencyCode);
        PdfPCell valueCell = new PdfPCell(new Phrase(formatted, billCurrencyFont(font.getSize(), bold, color)));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5.5f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (highlighted) valueCell.setBackgroundColor(new Color(238, 238, 238));
        table.addCell(valueCell);
    }

    private boolean isBillFontBold(Font font) {
        if ((font.getStyle() & Font.BOLD) == Font.BOLD) return true;
        if (font.getBaseFont() == null || font.getBaseFont().getPostscriptFontName() == null) return false;
        return font.getBaseFont().getPostscriptFontName().toLowerCase().contains("bold");
    }

    private void addBillNotes(Document document, Bill bill, Font body, Font heading) throws Exception {
        if (!StringUtils.hasText(bill.getNotes()) && !StringUtils.hasText(bill.getTermsAndConditions())) return;
        PdfPTable information = new PdfPTable(StringUtils.hasText(bill.getNotes()) && StringUtils.hasText(bill.getTermsAndConditions())
                ? new float[]{1, 1} : new float[]{1});
        information.setWidthPercentage(100);
        information.setSpacingBefore(20);
        if (StringUtils.hasText(bill.getNotes())) information.addCell(billInformationCell("Notes", bill.getNotes(), body, heading));
        if (StringUtils.hasText(bill.getTermsAndConditions())) information.addCell(billInformationCell("Terms & Conditions", bill.getTermsAndConditions(), body, heading));
        document.add(information);
    }

    private PdfPCell billInformationCell(String title, String content, Font body, Font heading) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColor(new Color(210, 210, 210));
        cell.setPadding(8);
        cell.addElement(new Paragraph(title, heading));
        cell.addElement(new Paragraph(content, body));
        return cell;
    }

    private byte[] buildPaymentReceiptPdf(Bill bill, BillPayment payment) { try {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 42, 42);
        PdfWriter.getInstance(document, output); document.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(14, 29, 73));
        Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(14, 29, 73));
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(51, 65, 85));

        PdfPTable header = new PdfPTable(new float[]{3, 2}); header.setWidthPercentage(100);
        header.addCell(noBorder(new Phrase("INTELLIATECH\nIntelliaTech Pvt. Ltd.\nIndia", heading)));
        PdfPCell receiptTitle = noBorder(new Phrase("PAYMENT RECEIPT\n" + payment.getPaymentNumber(), title));
        receiptTitle.setHorizontalAlignment(Element.ALIGN_RIGHT); header.addCell(receiptTitle); document.add(header);
        document.add(new Paragraph(" "));

        PdfPTable parties = new PdfPTable(new float[]{1, 1}); parties.setWidthPercentage(100);
        parties.addCell(box(new Phrase("Paid To\n" + bill.getVendorName() + vendorAddressText(bill.getVendor()), normal)));
        String paymentMeta = "Payment Date: " + payment.getPaymentDate() + "\nBill Number: " + bill.getBillNumber()
                + "\nPayment Mode: " + payment.getPaymentMode()
                + "\nPaid Through: " + payment.getPaidThrough()
                + (StringUtils.hasText(payment.getReferenceNumber()) ? "\nReference: " + payment.getReferenceNumber() : "");
        parties.addCell(box(new Phrase(paymentMeta, normal))); document.add(parties); document.add(new Paragraph(" "));

        PdfPTable totals = new PdfPTable(new float[]{3, 2}); totals.setWidthPercentage(58); totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addTotal(totals, "Payment Made", payment.getAmount(), bill.getCurrencyCode(), heading);
        addTotal(totals, "Bill Total", bill.getTotalAmount(), bill.getCurrencyCode(), normal);
        addTotal(totals, "Current Balance Due", bill.getBalanceDue(), bill.getCurrencyCode(), heading);
        document.add(totals);
        if (StringUtils.hasText(payment.getNotes())) document.add(new Paragraph("Notes\n" + payment.getNotes(), normal));
        document.close(); return output.toByteArray();
    } catch (Exception exception) { throw new IllegalStateException("Payment receipt PDF could not be generated.", exception); } }

    private BillCompanyProfile billCompanyProfile() {
        BusinessRecord organization = businessRecordRepository
                .findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization")
                .orElse(null);
        BusinessRecord branding = businessRecordRepository
                .findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "branding")
                .orElse(null);
        JsonNode details = json(organization == null ? null : organization.getNotes());
        JsonNode brandDetails = json(branding == null ? null : branding.getNotes());
        return new BillCompanyProfile(
                organization == null ? "IntelliaTech Pvt. Ltd." : value(organization.getPartyName()),
                details.path("address").asText(organization == null ? "" : value(organization.getPartyCity())),
                details.path("state").asText(""),
                details.path("pincode").asText(""),
                details.path("country").asText(""),
                organization == null ? "" : value(organization.getReferenceNumber()),
                organization == null ? "" : value(organization.getPartyPhone()),
                organization == null ? "" : value(organization.getPartyEmail()),
                details.path("website").asText(""),
                brandDetails.path("logoUrl").asText("")
        );
    }

    private JsonNode json(String content) {
        try {
            return StringUtils.hasText(content) ? objectMapper.readTree(content) : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String companyDetailsText(BillCompanyProfile company) {
        return Stream.of(
                        company.address(),
                        Stream.of(company.state(), company.pincode()).filter(StringUtils::hasText)
                                .reduce((left, right) -> left + " " + right).orElse(""),
                        company.country(),
                        StringUtils.hasText(company.gstin()) ? "GSTIN " + company.gstin() : "",
                        company.phone(),
                        company.email(),
                        company.website()
                )
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
    }

    private String vendorAddressLines(Vendor vendor) {
        if (vendor == null) return "";
        return Stream.of(
                        vendor.getBillingAddressLine1(),
                        vendor.getBillingAddressLine2(),
                        vendor.getBillingCity(),
                        Stream.of(vendor.getBillingPincode(), vendor.getBillingState()).filter(StringUtils::hasText)
                                .reduce((left, right) -> left + " " + right).orElse(""),
                        vendor.getBillingCountry(),
                        StringUtils.hasText(vendor.getGstin()) ? "GSTIN " + vendor.getGstin() : ""
                )
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
    }

    private Image billLogo(String logoUrl) {
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

    private Font billFont(float size, boolean bold, Color color) {
        List<String> candidates = bold
                ? List.of(
                        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
                        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
                )
                : List.of(
                        "/System/Library/Fonts/Supplemental/Arial.ttf",
                        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
                );
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

    private Font billCurrencyFont(float size, boolean bold, Color color) {
        List<String> candidates = List.of(
                "/System/Library/Fonts/Supplemental/Devanagari Sangam MN.ttc,0",
                "/System/Library/Fonts/Kohinoor.ttc,0",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        );
        for (String candidate : candidates) {
            try {
                String fontFile = candidate.contains(".ttc,")
                        ? candidate.substring(0, candidate.lastIndexOf(','))
                        : candidate;
                if (Files.isRegularFile(Path.of(fontFile))) {
                    BaseFont baseFont = BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    return new Font(baseFont, size, bold ? Font.BOLD : Font.NORMAL, color);
                }
            } catch (Exception ignored) {
                // Try the next currency-capable font before using the regular bill font.
            }
        }
        return billFont(size, bold, color);
    }

    private PdfPCell borderlessBillCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell borderlessBillCell(Phrase phrase, float padding) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(padding);
        return cell;
    }

    private String taxLabel(String component, Bill bill) {
        BigDecimal rate = bill.getItems().stream()
                .filter(item -> switch (component) {
                    case "CGST" -> decimal(item.getCgstAmount()).signum() > 0;
                    case "SGST" -> decimal(item.getSgstAmount()).signum() > 0;
                    default -> decimal(item.getIgstAmount()).signum() > 0;
                })
                .map(BillItem::getTaxRate)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (("CGST".equals(component) || "SGST".equals(component)) && rate.signum() > 0) {
            rate = rate.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        }
        if (rate.signum() == 0) return component;
        String percent = rate.stripTrailingZeros().toPlainString();
        return component + percent + " (" + percent + "%)";
    }

    private String formatBillDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String decimalText(BigDecimal amount) {
        return amountWithoutSymbol(amount);
    }

    private String currency(BigDecimal amount, String currencyCode) {
        String code = StringUtils.hasText(currencyCode) ? currencyCode.trim().toUpperCase(Locale.ROOT) : "INR";
        return "INR".equals(code) ? "₹" + amountWithoutSymbol(amount) : code + " " + amountWithoutSymbol(amount);
    }

    private String amountWithoutSymbol(BigDecimal amount) {
        BigDecimal normalized = money(amount).abs();
        String[] parts = normalized.toPlainString().split("\\.");
        String integer = parts[0];
        String grouped;
        if (integer.length() <= 3) {
            grouped = integer;
        } else {
            String suffix = integer.substring(integer.length() - 3);
            String prefix = integer.substring(0, integer.length() - 3);
            StringBuilder groups = new StringBuilder();
            while (prefix.length() > 2) {
                String tail = prefix.substring(prefix.length() - 2);
                groups.insert(0, "," + tail);
                prefix = prefix.substring(0, prefix.length() - 2);
            }
            grouped = prefix + groups + "," + suffix;
        }
        return (money(amount).signum() < 0 ? "-" : "") + grouped + "." + parts[1];
    }

    private String value(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private record BillCompanyProfile(
            String name,
            String address,
            String state,
            String pincode,
            String country,
            String gstin,
            String phone,
            String email,
            String website,
            String logoUrl
    ) { }

    private static final class BillPageFooter extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            canvas.setColorStroke(new Color(175, 175, 175));
            canvas.setLineWidth(0.5f);
            canvas.moveTo(document.left(), 27);
            canvas.lineTo(document.right(), 27);
            canvas.stroke();
            Font footer = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(80, 80, 80));
            ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                    new Phrase(String.valueOf(writer.getPageNumber()), footer),
                    (document.left() + document.right()) / 2, 15, 0);
        }
    }

    private PdfPCell noBorder(Phrase phrase) { PdfPCell cell = new PdfPCell(phrase); cell.setBorder(Rectangle.NO_BORDER); cell.setPadding(5); return cell; }
    private PdfPCell box(Phrase phrase) { PdfPCell cell = new PdfPCell(phrase); cell.setPadding(6); return cell; }
    private PdfPCell right(Phrase phrase) { PdfPCell cell = box(phrase); cell.setHorizontalAlignment(Element.ALIGN_RIGHT); return cell; }
    private void addTotal(PdfPTable table, String label, BigDecimal value, String currency, Font font) { PdfPCell left = noBorder(new Phrase(label, font));
        PdfPCell right = noBorder(new Phrase(currency + " " + money(value).toPlainString(), font)); right.setHorizontalAlignment(Element.ALIGN_RIGHT); table.addCell(left); table.addCell(right); }
    private String vendorAddressText(Vendor vendor) { if (vendor == null) return ""; return "\n" + joinNonBlank(vendor.getBillingAddressLine1(), vendor.getBillingAddressLine2(), vendor.getBillingCity(),
            vendor.getBillingState(), vendor.getBillingCountry(), vendor.getBillingPincode(), vendor.getGstin() == null ? null : "GSTIN " + vendor.getGstin()); }
    private String joinNonBlank(String... values) { return Arrays.stream(values).filter(StringUtils::hasText).collect(Collectors.joining(", ")); }

    private String amountInWords(BigDecimal amount) { long value = money(amount).setScale(0, RoundingMode.HALF_UP).longValue();
        if (value == 0) return "Zero Rupees Only"; return toIndianWords(value) + " Rupees Only"; }
    private String toIndianWords(long value) { if (value < 20) return List.of("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen").get((int) value);
        if (value < 100) { String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"}; return tens[(int) (value / 10)] + (value % 10 == 0 ? "" : " " + toIndianWords(value % 10)); }
        if (value < 1000) return toIndianWords(value / 100) + " Hundred" + (value % 100 == 0 ? "" : " " + toIndianWords(value % 100));
        if (value < 100000) return toIndianWords(value / 1000) + " Thousand" + (value % 1000 == 0 ? "" : " " + toIndianWords(value % 1000));
        if (value < 10000000) return toIndianWords(value / 100000) + " Lakh" + (value % 100000 == 0 ? "" : " " + toIndianWords(value % 100000));
        return toIndianWords(value / 10000000) + " Crore" + (value % 10000000 == 0 ? "" : " " + toIndianWords(value % 10000000)); }

    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal decimal(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String firstNonBlank(String... values) { for (String value : values) if (StringUtils.hasText(value)) return value.trim(); return null; }
    private String code(SupplyState state) { return state == null ? null : state.getCode(); }
    private String name(SupplyState state) { return state == null ? "" : state.getName(); }
    private String extractStateCode(String value) { if (!StringUtils.hasText(value)) return null; var matcher = java.util.regex.Pattern.compile("(?:\\(|^)(\\d{2})(?:\\)|$)").matcher(value); return matcher.find() ? matcher.group(1) : null; }
    private String currentUser() { var authentication = SecurityContextHolder.getContext().getAuthentication(); return authentication == null || !authentication.isAuthenticated() ? "System" : authentication.getName(); }
}
