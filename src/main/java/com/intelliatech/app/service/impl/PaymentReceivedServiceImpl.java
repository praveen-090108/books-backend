package com.intelliatech.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.PaymentReceivedAllocationRequest;
import com.intelliatech.app.dto.request.PaymentReceivedRequest;
import com.intelliatech.app.dto.request.ReverseInvoicePaymentRequest;
import com.intelliatech.app.dto.response.EligibleInvoiceForPaymentResponse;
import com.intelliatech.app.dto.response.InvoiceLifecycleResponse;
import com.intelliatech.app.dto.response.PaymentReceivedAllocationResponse;
import com.intelliatech.app.dto.response.PaymentReceivedResponse;
import com.intelliatech.app.dto.response.PaymentReceivedSummaryResponse;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.InvoiceCounter;
import com.intelliatech.app.entity.InvoiceCreditAllocation;
import com.intelliatech.app.entity.InvoiceLifecycle;
import com.intelliatech.app.entity.InvoicePayment;
import com.intelliatech.app.entity.InvoicePaymentAllocation;
import com.intelliatech.app.entity.InvoiceStatus;
import com.intelliatech.app.entity.InvoiceTdsDeduction;
import com.intelliatech.app.entity.PaymentReceipt;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.InvoiceCounterRepository;
import com.intelliatech.app.repository.InvoiceCreditAllocationRepository;
import com.intelliatech.app.repository.InvoiceLifecycleRepository;
import com.intelliatech.app.repository.InvoicePaymentAllocationRepository;
import com.intelliatech.app.repository.InvoicePaymentRepository;
import com.intelliatech.app.repository.InvoiceTdsDeductionRepository;
import com.intelliatech.app.repository.PaymentReceiptRepository;
import com.intelliatech.app.service.InvoiceLifecycleService;
import com.intelliatech.app.service.PaymentReceivedService;
import com.intelliatech.app.service.BankAccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PaymentReceivedServiceImpl implements PaymentReceivedService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Set<InvoiceStatus> ELIGIBLE_STATUSES = Set.of(
            InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE
    );
    private static final Set<String> PAYMENT_MODES = Set.of(
            "CASH", "BANK_TRANSFER", "UPI", "CREDIT_CARD", "DEBIT_CARD", "CHEQUE", "PAYPAL",
            "WISE", "NEFT", "RTGS", "IMPS", "OTHER"
    );

    private final BusinessRecordRepository businessRecordRepository;
    private final InvoiceLifecycleRepository lifecycleRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final InvoicePaymentAllocationRepository allocationRepository;
    private final InvoiceTdsDeductionRepository tdsRepository;
    private final PaymentReceiptRepository receiptRepository;
    private final InvoiceCreditAllocationRepository creditAllocationRepository;
    private final InvoiceCounterRepository counterRepository;
    private final InvoiceLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;
    private final BankAccountService bankAccountService;

    @Override
    @Transactional
    public List<EligibleInvoiceForPaymentResponse> findEligibleInvoices(Long customerId, Long paymentId) {
        BusinessRecord customer = activeCustomer(customerId);
        Map<Long, BigDecimal> currentAllocations = paymentId == null ? Map.of()
                : allocationRepository.findAllByPaymentIdOrderById(paymentId).stream().collect(Collectors.toMap(
                        allocation -> allocation.getInvoice().getId(), this::settlement, BigDecimal::add));
        List<EligibleInvoiceForPaymentResponse> result = new ArrayList<>();
        for (BusinessRecord invoice : customerInvoices(customer)) {
            if (!belongsToCustomer(invoice, customer)) continue;
            InvoiceLifecycleResponse lifecycle = lifecycleService.findByInvoiceId(invoice.getId());
            InvoiceStatus status = statusFrom(lifecycle.status());
            BigDecimal restoredBalance = money(lifecycle.balanceDue()).add(money(currentAllocations.get(invoice.getId())));
            if ((!ELIGIBLE_STATUSES.contains(status) && !currentAllocations.containsKey(invoice.getId()))
                    || restoredBalance.signum() <= 0) continue;
            result.add(new EligibleInvoiceForPaymentResponse(
                    invoice.getId(), invoice.getRecordNumber(), invoice.getRecordDate(), invoice.getDueDate(),
                    lifecycle.status(), money(invoice.getAmount()), money(lifecycle.cashAmountPaid()),
                    money(lifecycle.tdsSettled()), money(lifecycle.creditApplied()), money(lifecycle.creditNoteApplied()),
                    money(restoredBalance), tdsBase(invoice, configuredTdsBaseType()), currency(invoice)
            ));
        }
        return result.stream()
                .sorted(Comparator.comparing(EligibleInvoiceForPaymentResponse::dueDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReceivedSummaryResponse summary() {
        List<InvoicePayment> activePayments = paymentRepository.findAllByOrderByPaymentDateDescCreatedAtDesc().stream()
                .filter(payment -> !Boolean.TRUE.equals(payment.getReversed()))
                .toList();
        BigDecimal total = activePayments.stream().map(InvoicePayment::getGrossAmountReceived)
                .map(this::money).reduce(ZERO, BigDecimal::add);
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        BigDecimal monthTotal = activePayments.stream()
                .filter(payment -> YearMonth.from(payment.getPaymentDate()).equals(month))
                .map(InvoicePayment::getGrossAmountReceived).map(this::money).reduce(ZERO, BigDecimal::add);

        int startMonth = financialYearStartMonth();
        LocalDate yearStart = LocalDate.of(today.getMonthValue() < startMonth ? today.getYear() - 1 : today.getYear(), startMonth, 1);
        LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);
        BigDecimal yearTotal = activePayments.stream()
                .filter(payment -> !payment.getPaymentDate().isBefore(yearStart) && !payment.getPaymentDate().isAfter(yearEnd))
                .map(InvoicePayment::getGrossAmountReceived).map(this::money).reduce(ZERO, BigDecimal::add);

        List<InvoiceLifecycle> overdue = lifecycleRepository.findAllByStatusIn(List.of(InvoiceStatus.OVERDUE)).stream()
                .filter(lifecycle -> money(lifecycle.getBalanceDue()).signum() > 0)
                .toList();
        BigDecimal overdueAmount = overdue.stream().map(InvoiceLifecycle::getBalanceDue)
                .map(this::money).reduce(ZERO, BigDecimal::add);
        String yearLabel = startMonth == 1
                ? String.valueOf(yearStart.getYear())
                : "%d-%02d".formatted(yearStart.getYear(), (yearStart.getYear() + 1) % 100);
        return new PaymentReceivedSummaryResponse(money(total), money(monthTotal),
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), month.atDay(1), month.atEndOfMonth(),
                money(yearTotal), yearLabel, yearStart, yearEnd,
                money(overdueAmount), overdue.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentReceivedResponse> findAll(
            int page, int size, String search, Long customerId, String invoiceNumber,
            LocalDate fromDate, LocalDate toDate, String paymentMode, Boolean tds, String status,
            String sortBy, String sortDirection
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String query = normalize(search);
        String invoiceQuery = normalize(invoiceNumber);
        String mode = normalizeKey(paymentMode);
        String paymentStatus = normalizeKey(status);
        List<PaymentReceivedResponse> filtered = paymentRepository.findAllByOrderByPaymentDateDescCreatedAtDesc().stream()
                .map(this::response)
                .filter(payment -> customerId == null || customerId.equals(payment.customerId()))
                .filter(payment -> fromDate == null || !payment.paymentDate().isBefore(fromDate))
                .filter(payment -> toDate == null || !payment.paymentDate().isAfter(toDate))
                .filter(payment -> !StringUtils.hasText(mode) || mode.equals(normalizeKey(payment.paymentMode())))
                .filter(payment -> tds == null || tds == (payment.tdsAmount().signum() > 0))
                .filter(payment -> !StringUtils.hasText(paymentStatus) || paymentStatus.equals(normalizeKey(payment.status())))
                .filter(payment -> !StringUtils.hasText(invoiceQuery) || payment.allocations().stream()
                        .anyMatch(allocation -> normalize(allocation.invoiceNumber()).contains(invoiceQuery)))
                .filter(payment -> !StringUtils.hasText(query) || matchesSearch(payment, query))
                .sorted(comparator(sortBy, sortDirection))
                .toList();
        int start = Math.min(safePage * safeSize, filtered.size());
        int end = Math.min(start + safeSize, filtered.size());
        return new PageImpl<>(filtered.subList(start, end), PageRequest.of(safePage, safeSize), filtered.size());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReceivedResponse findById(Long id) {
        return response(payment(id));
    }

    @Override
    @Transactional
    public PaymentReceivedResponse create(PaymentReceivedRequest request) {
        if (!StringUtils.hasText(request.idempotencyKey())) {
            throw new IllegalArgumentException("Payment request key is required. Please retry the payment.");
        }
        var existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey().trim());
        if (existing.isPresent()) return response(existing.get());
        PreparedPayment prepared = prepare(request, Map.of());
        InvoicePayment payment = new InvoicePayment();
        payment.setPaymentNumber(nextNumber("PAYMENT", "PAY"));
        payment.setIdempotencyKey(request.idempotencyKey().trim());
        applyPaymentFields(payment, request, prepared);
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("This payment was already submitted.");
        }
        saveAllocations(payment, prepared);
        saveTds(payment, request, prepared);
        refreshInvoices(prepared.invoiceIds());
        saveReceipt(payment, remainingBalance(prepared.invoiceIds()));
        return response(payment);
    }

    @Override
    @Transactional
    public PaymentReceivedResponse update(Long id, PaymentReceivedRequest request) {
        InvoicePayment payment = payment(id);
        assertEditable(payment);
        List<InvoicePaymentAllocation> oldAllocations = allocationRepository.findAllByPaymentIdOrderById(id);
        Map<Long, BigDecimal> priorSettlement = oldAllocations.stream().collect(Collectors.toMap(
                allocation -> allocation.getInvoice().getId(),
                allocation -> settlement(allocation),
                BigDecimal::add,
                LinkedHashMap::new
        ));
        Set<Long> affectedInvoices = oldAllocations.stream().map(allocation -> allocation.getInvoice().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        PreparedPayment prepared = prepare(request, priorSettlement);
        affectedInvoices.addAll(prepared.invoiceIds());

        removePaymentCredits(payment, oldAllocations, false);
        allocationRepository.deleteAll(oldAllocations);
        allocationRepository.flush();
        tdsRepository.findByPaymentId(id).ifPresent(tdsRepository::delete);
        tdsRepository.flush();

        applyPaymentFields(payment, request, prepared);
        paymentRepository.save(payment);
        saveAllocations(payment, prepared);
        saveTds(payment, request, prepared);
        refreshInvoices(affectedInvoices);
        saveReceipt(payment, remainingBalance(prepared.invoiceIds()));
        return response(payment);
    }

    @Override
    @Transactional
    public PaymentReceivedResponse reverse(Long id, ReverseInvoicePaymentRequest request) {
        InvoicePayment payment = payment(id);
        assertEditable(payment);
        List<InvoicePaymentAllocation> allocations = allocationRepository.findAllByPaymentIdOrderById(id);
        payment.setReversed(true);
        payment.setReversedAt(LocalDateTime.now());
        payment.setReversalReason(StringUtils.hasText(request.reason()) ? request.reason().trim() : "Payment reversed");
        paymentRepository.save(payment);
        removePaymentCredits(payment, allocations, true);
        refreshInvoices(invoiceIds(allocations));
        return response(payment);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        InvoicePayment payment = payment(id);
        if (Boolean.TRUE.equals(payment.getReconciled())) {
            throw new IllegalStateException("Reconciled payments cannot be deleted.");
        }
        List<InvoicePaymentAllocation> allocations = allocationRepository.findAllByPaymentIdOrderById(id);
        Set<Long> invoiceIds = invoiceIds(allocations);
        removePaymentCredits(payment, allocations, false);
        receiptRepository.findByPaymentId(id).ifPresent(receiptRepository::delete);
        tdsRepository.findByPaymentId(id).ifPresent(tdsRepository::delete);
        allocationRepository.deleteAll(allocations);
        paymentRepository.delete(payment);
        paymentRepository.flush();
        refreshInvoices(invoiceIds);
    }

    private PreparedPayment prepare(PaymentReceivedRequest request, Map<Long, BigDecimal> priorSettlement) {
        BusinessRecord customer = activeCustomer(request.customerId());
        if (request.paymentDate() == null) throw new IllegalArgumentException("Payment date is required.");
        if (request.allocations() == null || request.allocations().isEmpty()) {
            throw new IllegalArgumentException("Allocate the payment to at least one invoice.");
        }
        BigDecimal gross = money(request.amountReceived());
        BigDecimal charges = money(request.bankCharges());
        if (gross.signum() < 0) throw new IllegalArgumentException("Amount received cannot be negative.");
        if (charges.signum() < 0) throw new IllegalArgumentException("Bank charges cannot be negative.");
        if (charges.compareTo(gross) > 0) throw new IllegalArgumentException("Bank charges cannot exceed the amount received.");
        String mode = normalizeKey(request.paymentMode());
        if (gross.signum() > 0) {
            if (!PAYMENT_MODES.contains(mode)) throw new IllegalArgumentException("Select a valid payment mode.");
            if (requiresDepositAccount(mode) && request.bankAccountId() == null && !StringUtils.hasText(request.depositAccount())) {
                throw new IllegalArgumentException("Deposit To / Bank Account is required for this payment mode.");
            }
        }
        BigDecimal tdsRate = decimal4(request.tdsPercentage());
        if (tdsRate.signum() < 0 || tdsRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("TDS percentage must be between 0 and 100.");
        }

        Map<Long, PaymentReceivedAllocationRequest> unique = new LinkedHashMap<>();
        for (PaymentReceivedAllocationRequest allocation : request.allocations()) {
            if (allocation.invoiceId() == null) throw new IllegalArgumentException("An invoice is required for every allocation.");
            if (unique.put(allocation.invoiceId(), allocation) != null) {
                throw new IllegalArgumentException("The same invoice cannot be allocated twice.");
            }
        }
        List<Long> sortedIds = unique.keySet().stream().sorted().toList();
        Map<Long, InvoiceLifecycle> lifecycles = sortedIds.stream().collect(Collectors.toMap(
                Function.identity(), this::lockedLifecycle, (left, right) -> left, LinkedHashMap::new
        ));
        List<PreparedAllocation> allocations = new ArrayList<>();
        BigDecimal cashAllocated = ZERO;
        BigDecimal tdsAllocated = ZERO;
        BigDecimal creditAllocated = ZERO;
        BigDecimal aggregateTdsBase = ZERO;
        String baseType = configuredTdsBaseType();
        for (Long invoiceId : sortedIds) {
            InvoiceLifecycle lifecycle = lifecycles.get(invoiceId);
            BusinessRecord invoice = lifecycle.getInvoice();
            if (!belongsToCustomer(invoice, customer)) {
                throw new IllegalArgumentException("Invoice " + invoice.getRecordNumber() + " does not belong to the selected customer.");
            }
            if (!ELIGIBLE_STATUSES.contains(lifecycle.getStatus()) && !priorSettlement.containsKey(invoiceId)) {
                throw new IllegalStateException("Invoice " + invoice.getRecordNumber() + " is not eligible for payment.");
            }
            PaymentReceivedAllocationRequest source = unique.get(invoiceId);
            BigDecimal cash = money(source.paymentApplied());
            BigDecimal tds = money(source.tdsApplied());
            BigDecimal credit = money(source.creditApplied());
            if (cash.signum() < 0 || tds.signum() < 0 || credit.signum() < 0) {
                throw new IllegalArgumentException("Invoice allocations cannot be negative.");
            }
            if (!Boolean.TRUE.equals(request.tdsDeducted()) && tds.signum() > 0) {
                throw new IllegalArgumentException("Select Yes, TDS before applying TDS to an invoice.");
            }
            BigDecimal eligible = money(lifecycle.getBalanceDue()).add(money(priorSettlement.get(invoiceId)));
            BigDecimal allocated = money(cash.add(tds).add(credit));
            if (allocated.signum() <= 0) throw new IllegalArgumentException("Enter an allocation for " + invoice.getRecordNumber() + ".");
            if (allocated.compareTo(eligible) > 0) {
                throw new IllegalArgumentException("Allocation for " + invoice.getRecordNumber() + " exceeds its outstanding balance.");
            }
            BigDecimal invoiceTdsBase = tdsBase(invoice, baseType);
            if (tds.compareTo(invoiceTdsBase) > 0) {
                throw new IllegalArgumentException("TDS for " + invoice.getRecordNumber() + " exceeds its eligible TDS base.");
            }
            allocations.add(new PreparedAllocation(invoice, cash, tds, credit));
            cashAllocated = cashAllocated.add(cash);
            tdsAllocated = tdsAllocated.add(tds);
            creditAllocated = creditAllocated.add(credit);
            aggregateTdsBase = aggregateTdsBase.add(invoiceTdsBase);
        }
        cashAllocated = money(cashAllocated);
        tdsAllocated = money(tdsAllocated);
        creditAllocated = money(creditAllocated);
        if (gross.compareTo(cashAllocated) != 0) {
            throw new IllegalArgumentException("Amount received must equal the total payment applied to invoices.");
        }
        BigDecimal requestedTds = Boolean.TRUE.equals(request.tdsDeducted())
                ? money(request.tdsAmount() == null ? tdsAllocated : request.tdsAmount()) : ZERO;
        if (requestedTds.compareTo(tdsAllocated) != 0) {
            throw new IllegalArgumentException("TDS amount must equal the total TDS allocated to invoices.");
        }
        if (requestedTds.compareTo(aggregateTdsBase) > 0) {
            throw new IllegalArgumentException("TDS amount cannot exceed the eligible TDS base.");
        }
        BigDecimal available = money(gross.add(requestedTds).add(creditAllocated));
        BigDecimal allocated = money(cashAllocated.add(tdsAllocated).add(creditAllocated));
        if (available.compareTo(allocated) != 0) {
            throw new IllegalArgumentException("Allocate the full payment amount before saving.");
        }
        return new PreparedPayment(customer, allocations, gross, charges, money(gross.subtract(charges)),
                requestedTds, creditAllocated, baseType, money(aggregateTdsBase), tdsRate);
    }

    private void applyPaymentFields(InvoicePayment payment, PaymentReceivedRequest request, PreparedPayment prepared) {
        payment.setPrimaryInvoice(prepared.allocations().get(0).invoice());
        payment.setCustomerId(prepared.customer().getId());
        payment.setCustomerName(prepared.customer().getPartyName());
        payment.setPaymentDate(request.paymentDate());
        payment.setPaymentMode(displayKey(normalizeKey(request.paymentMode())));
        Long retainedBankAccountId = payment.getBankAccount() == null ? null : payment.getBankAccount().getId();
        var bankAccount = bankAccountService.selection(request.bankAccountId(), retainedBankAccountId);
        payment.setBankAccount(bankAccount);
        payment.setDepositAccount(bankAccount == null ? trim(request.depositAccount()) : bankAccount.getAccountName());
        payment.setReferenceNumber(trim(request.referenceNumber()));
        payment.setGrossAmountReceived(prepared.gross());
        payment.setBankCharges(prepared.charges());
        payment.setNetBankCredit(prepared.net());
        payment.setNotes(trim(request.notes()));
        payment.setAttachmentUrl(trim(request.attachmentUrl()));
        payment.setSendThankYouEmail(Boolean.TRUE.equals(request.sendThankYouEmail()));
        payment.setBankAccountName(trim(request.bankAccountName()));
        payment.setBankName(trim(request.bankName()));
        payment.setMaskedAccountNumber(trim(request.maskedAccountNumber()));
        payment.setTransactionId(trim(request.transactionId()));
        payment.setChequeNumber(trim(request.chequeNumber()));
        payment.setChequeDate(request.chequeDate());
        if (payment.getCreatedBy() == null) payment.setCreatedBy(currentUser());
    }

    private void saveAllocations(InvoicePayment payment, PreparedPayment prepared) {
        for (PreparedAllocation source : prepared.allocations()) {
            InvoicePaymentAllocation allocation = new InvoicePaymentAllocation();
            allocation.setPayment(payment);
            allocation.setInvoice(source.invoice());
            allocation.setCashAmountApplied(source.cash());
            allocation.setTdsAmountApplied(source.tds());
            allocation.setCreditAmountApplied(source.credit());
            allocationRepository.save(allocation);
            if (source.credit().signum() > 0) {
                InvoiceCreditAllocation credit = new InvoiceCreditAllocation();
                credit.setInvoice(source.invoice());
                credit.setSourceType("PAYMENT");
                credit.setSourceReference(payment.getPaymentNumber());
                credit.setAmount(source.credit());
                credit.setAppliedOn(payment.getPaymentDate());
                credit.setReversed(false);
                creditAllocationRepository.save(credit);
            }
        }
    }

    private void saveTds(InvoicePayment payment, PaymentReceivedRequest request, PreparedPayment prepared) {
        if (prepared.tds().signum() <= 0) return;
        InvoiceTdsDeduction tds = new InvoiceTdsDeduction();
        tds.setPayment(payment);
        tds.setBaseType(prepared.tdsBaseType());
        tds.setBaseAmount(prepared.tdsBase());
        tds.setPercentage(prepared.tdsRate());
        tds.setAmount(prepared.tds());
        tds.setSectionCode(trim(request.tdsSectionCode()));
        tds.setCertificateNumber(trim(request.tdsCertificateNumber()));
        tds.setCertificateDate(request.tdsCertificateDate());
        tds.setRemarks(trim(request.tdsRemarks()));
        tdsRepository.save(tds);
    }

    private void saveReceipt(InvoicePayment payment, BigDecimal remainingBalance) {
        PaymentReceipt receipt = receiptRepository.findByPaymentId(payment.getId()).orElseGet(PaymentReceipt::new);
        receipt.setPayment(payment);
        if (!StringUtils.hasText(receipt.getReceiptNumber())) receipt.setReceiptNumber(nextNumber("RECEIPT", "RCP"));
        receipt.setRemainingBalance(money(remainingBalance));
        receiptRepository.save(receipt);
    }

    private PaymentReceivedResponse response(InvoicePayment payment) {
        List<InvoicePaymentAllocation> allocations = allocationRepository.findAllByPaymentIdOrderById(payment.getId());
        InvoiceTdsDeduction tds = tdsRepository.findByPaymentId(payment.getId()).orElse(null);
        PaymentReceipt receipt = receiptRepository.findByPaymentId(payment.getId()).orElse(null);
        BusinessRecord customer = resolvePaymentCustomer(payment, allocations);
        BigDecimal allocated = allocations.stream().map(this::settlement).reduce(ZERO, BigDecimal::add);
        BigDecimal tdsAmount = allocations.stream().map(InvoicePaymentAllocation::getTdsAmountApplied)
                .map(this::money).reduce(ZERO, BigDecimal::add);
        BigDecimal credits = allocations.stream().map(InvoicePaymentAllocation::getCreditAmountApplied)
                .map(this::money).reduce(ZERO, BigDecimal::add);
        BigDecimal available = money(payment.getGrossAmountReceived()).add(tdsAmount).add(credits);
        List<PaymentReceivedAllocationResponse> allocationResponses = allocations.stream().map(allocation -> {
            BusinessRecord invoice = allocation.getInvoice();
            BigDecimal balance = lifecycleRepository.findByInvoiceId(invoice.getId())
                    .map(InvoiceLifecycle::getBalanceDue).map(this::money).orElse(money(invoice.getBalanceAmount()));
            return new PaymentReceivedAllocationResponse(
                    invoice.getId(), invoice.getRecordNumber(), invoice.getRecordDate(), invoice.getDueDate(),
                    money(invoice.getAmount()), money(allocation.getCashAmountApplied()),
                    money(allocation.getTdsAmountApplied()), money(allocation.getCreditAmountApplied()), balance
            );
        }).toList();
        return new PaymentReceivedResponse(
                payment.getId(), payment.getPaymentNumber(), receipt == null ? "" : receipt.getReceiptNumber(),
                customer == null ? payment.getCustomerId() : customer.getId(),
                customer == null ? value(payment.getCustomerName()) : value(customer.getPartyName()),
                customer == null ? "" : value(customer.getPartyEmail()),
                customer == null ? "" : value(customer.getPartyPhone()), customerAddress(customer), payment.getPaymentDate(),
                value(payment.getPaymentMode()), value(payment.getDepositAccount()), payment.getBankAccount() == null ? null : payment.getBankAccount().getId(), value(payment.getReferenceNumber()),
                money(payment.getGrossAmountReceived()), money(tdsAmount), money(payment.getBankCharges()),
                money(payment.getNetBankCredit()), money(allocated), money(available.subtract(allocated).max(ZERO)),
                Boolean.TRUE.equals(payment.getReversed()) ? "Reversed" : "Paid", value(payment.getBankAccountName()),
                value(payment.getBankName()), value(payment.getMaskedAccountNumber()), value(payment.getTransactionId()),
                value(payment.getChequeNumber()), payment.getChequeDate(), tds == null ? "" : value(tds.getBaseType()),
                tds == null ? ZERO : money(tds.getBaseAmount()), tds == null ? BigDecimal.ZERO : tds.getPercentage(),
                tds == null ? "" : value(tds.getSectionCode()), tds == null ? "" : value(tds.getCertificateNumber()),
                tds == null ? null : tds.getCertificateDate(), tds == null ? "" : value(tds.getRemarks()),
                value(payment.getNotes()), value(payment.getAttachmentUrl()), Boolean.TRUE.equals(payment.getSendThankYouEmail()),
                Boolean.TRUE.equals(payment.getReconciled()), Boolean.TRUE.equals(payment.getReversed()), payment.getReversedAt(),
                value(payment.getReversalReason()), allocationResponses, payment.getCreatedAt()
        );
    }

    private BusinessRecord resolvePaymentCustomer(
            InvoicePayment payment,
            List<InvoicePaymentAllocation> allocations
    ) {
        if (payment.getCustomerId() != null) {
            BusinessRecord customer = businessRecordRepository
                    .findByModuleAndTypeAndId("sales", "customers", payment.getCustomerId())
                    .orElse(null);
            if (customer != null) return customer;
        }
        if (StringUtils.hasText(payment.getCustomerName())) {
            BusinessRecord customer = businessRecordRepository
                    .findFirstByModuleAndTypeAndPartyNameIgnoreCase(
                            "sales", "customers", payment.getCustomerName().trim())
                    .orElse(null);
            if (customer != null) return customer;
        }
        if (!allocations.isEmpty() && StringUtils.hasText(allocations.get(0).getInvoice().getPartyName())) {
            return businessRecordRepository.findFirstByModuleAndTypeAndPartyNameIgnoreCase(
                    "sales", "customers", allocations.get(0).getInvoice().getPartyName()).orElse(null);
        }
        return null;
    }

    private void removePaymentCredits(InvoicePayment payment, List<InvoicePaymentAllocation> allocations, boolean reverse) {
        for (InvoicePaymentAllocation allocation : allocations) {
            creditAllocationRepository.findByInvoiceIdAndSourceTypeAndSourceReference(
                    allocation.getInvoice().getId(), "PAYMENT", payment.getPaymentNumber()).ifPresent(credit -> {
                if (reverse) {
                    credit.setReversed(true);
                    creditAllocationRepository.save(credit);
                } else {
                    creditAllocationRepository.delete(credit);
                }
            });
        }
        creditAllocationRepository.flush();
    }

    private void refreshInvoices(Iterable<Long> invoiceIds) {
        for (Long invoiceId : invoiceIds) lifecycleService.refreshOverdueStatus(invoiceId);
    }

    private BigDecimal remainingBalance(Iterable<Long> invoiceIds) {
        BigDecimal remaining = ZERO;
        for (Long invoiceId : invoiceIds) {
            remaining = remaining.add(lifecycleRepository.findByInvoiceId(invoiceId)
                    .map(InvoiceLifecycle::getBalanceDue).map(this::money).orElse(ZERO));
        }
        return money(remaining);
    }

    private InvoiceLifecycle lockedLifecycle(Long invoiceId) {
        return lifecycleRepository.findByInvoiceIdForUpdate(invoiceId).orElseThrow(() ->
                new ResourceNotFoundException("Invoice lifecycle not found for invoice " + invoiceId + "."));
    }

    private BusinessRecord activeCustomer(Long customerId) {
        if (customerId == null) throw new IllegalArgumentException("Customer is required.");
        BusinessRecord customer = businessRecordRepository.findByModuleAndTypeAndId("sales", "customers", customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (!"ACTIVE".equals(normalizeKey(customer.getStatus()))) {
            throw new IllegalStateException("Select an active customer.");
        }
        return customer;
    }

    private List<BusinessRecord> customerInvoices(BusinessRecord customer) {
        return businessRecordRepository.findAllByModuleAndTypeAndPartyNameIgnoreCaseOrderByRecordDateDesc(
                "sales", "invoices", customer.getPartyName());
    }

    private boolean belongsToCustomer(BusinessRecord invoice, BusinessRecord customer) {
        Long invoiceCustomerId = longValue(json(invoice.getNotes()), "Customer ID", "customerId");
        return invoiceCustomerId != null
                ? customer.getId().equals(invoiceCustomerId)
                : customer.getPartyName().equalsIgnoreCase(invoice.getPartyName());
    }

    private InvoicePayment payment(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found."));
    }

    private void assertEditable(InvoicePayment payment) {
        if (Boolean.TRUE.equals(payment.getReconciled())) throw new IllegalStateException("Reconciled payments cannot be changed.");
        if (Boolean.TRUE.equals(payment.getReversed())) throw new IllegalStateException("Reversed payments cannot be changed.");
    }

    private Set<Long> invoiceIds(List<InvoicePaymentAllocation> allocations) {
        return allocations.stream().map(allocation -> allocation.getInvoice().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private BigDecimal settlement(InvoicePaymentAllocation allocation) {
        return money(allocation.getCashAmountApplied()).add(money(allocation.getTdsAmountApplied()))
                .add(money(allocation.getCreditAmountApplied()));
    }

    private String nextNumber(String counterKey, String prefix) {
        InvoiceCounter counter = counterRepository.findByKeyForUpdate(counterKey).orElseGet(() -> {
            InvoiceCounter created = new InvoiceCounter();
            created.setKey(counterKey);
            created.setNextValue(1L);
            return counterRepository.saveAndFlush(created);
        });
        long sequence = counter.getNextValue();
        counter.setNextValue(sequence + 1);
        counterRepository.save(counter);
        return "%s-%d-%06d".formatted(prefix, Year.now().getValue(), sequence);
    }

    private int financialYearStartMonth() {
        return businessRecordRepository.findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization")
                .map(record -> json(record.getNotes()))
                .map(notes -> notes.path("financialYearStartMonth").asInt(1))
                .filter(month -> month >= Month.JANUARY.getValue() && month <= Month.DECEMBER.getValue())
                .orElse(1);
    }

    private String configuredTdsBaseType() {
        return businessRecordRepository.findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization")
                .map(record -> json(record.getNotes()).path("tdsBaseType").asText("TAXABLE_VALUE"))
                .map(this::normalizeKey)
                .filter(value -> Set.of("INVOICE_TOTAL", "TAXABLE_VALUE").contains(value))
                .orElse("TAXABLE_VALUE");
    }

    private BigDecimal tdsBase(BusinessRecord invoice, String baseType) {
        if (!"TAXABLE_VALUE".equals(baseType)) return money(invoice.getAmount());
        JsonNode root = json(invoice.getNotes());
        JsonNode totals = root.path("totals");
        if (totals.isMissingNode()) totals = root.path("Invoice Totals");
        BigDecimal taxable = decimal(totals.path("taxableAmount"));
        return taxable.signum() > 0 ? taxable : money(invoice.getAmount());
    }

    private String currency(BusinessRecord invoice) {
        String currency = text(json(invoice.getNotes()), "Currency", "currency");
        return StringUtils.hasText(currency) ? currency : "INR - Indian Rupee";
    }

    private String customerAddress(BusinessRecord customer) {
        if (customer == null) return "";
        JsonNode notes = json(customer.getNotes());
        return List.of(
                text(notes, "billingAddress", "Customer Billing Address", "addressLine1"),
                text(notes, "addressLine2"), value(customer.getPartyCity()), text(notes, "state", "billingState"),
                text(notes, "country"), text(notes, "pinCode", "pincode", "zipCode")
        ).stream().filter(StringUtils::hasText).collect(Collectors.joining(", "));
    }

    private boolean matchesSearch(PaymentReceivedResponse payment, String query) {
        return normalize(payment.paymentNumber()).contains(query)
                || normalize(payment.receiptNumber()).contains(query)
                || normalize(payment.customerName()).contains(query)
                || normalize(payment.referenceNumber()).contains(query)
                || payment.allocations().stream().anyMatch(allocation -> normalize(allocation.invoiceNumber()).contains(query));
    }

    private Comparator<PaymentReceivedResponse> comparator(String sortBy, String direction) {
        Comparator<PaymentReceivedResponse> comparator = switch (normalizeKey(sortBy)) {
            case "PAYMENT_NUMBER" -> Comparator.comparing(PaymentReceivedResponse::paymentNumber,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "CUSTOMER" -> Comparator.comparing(PaymentReceivedResponse::customerName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "AMOUNT", "GROSS_AMOUNT_RECEIVED" -> Comparator.comparing(PaymentReceivedResponse::grossAmountReceived);
            default -> Comparator.comparing(PaymentReceivedResponse::paymentDate,
                    Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(PaymentReceivedResponse::createdAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "ASC".equals(normalizeKey(direction)) ? comparator : comparator.reversed();
    }

    private JsonNode json(String value) {
        if (!StringUtils.hasText(value)) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    private Long longValue(JsonNode node, String... fields) {
        String value = text(node, fields);
        if (!StringUtils.hasText(value)) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return ZERO;
        try {
            return money(node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText("0").replace(",", "")));
        } catch (NumberFormatException ignored) {
            return ZERO;
        }
    }

    private InvoiceStatus statusFrom(String value) {
        try {
            return InvoiceStatus.valueOf(normalizeKey(value));
        } catch (Exception ignored) {
            return InvoiceStatus.DRAFT;
        }
    }

    private boolean requiresDepositAccount(String mode) {
        return !Set.of("CASH", "OTHER").contains(mode);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal4(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private String displayKey(String value) {
        if (!StringUtils.hasText(value)) return null;
        return java.util.Arrays.stream(value.split("_"))
                .map(word -> word.substring(0, 1) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && StringUtils.hasText(authentication.getName()) ? authentication.getName() : "system";
    }

    private record PreparedAllocation(BusinessRecord invoice, BigDecimal cash, BigDecimal tds, BigDecimal credit) {
    }

    private record PreparedPayment(
            BusinessRecord customer,
            List<PreparedAllocation> allocations,
            BigDecimal gross,
            BigDecimal charges,
            BigDecimal net,
            BigDecimal tds,
            BigDecimal credit,
            String tdsBaseType,
            BigDecimal tdsBase,
            BigDecimal tdsRate
    ) {
        Set<Long> invoiceIds() {
            return allocations.stream().map(allocation -> allocation.invoice().getId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
