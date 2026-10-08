package com.intelliatech.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.ApplyInvoiceCreditNoteRequest;
import com.intelliatech.app.dto.request.BusinessRecordRequest;
import com.intelliatech.app.dto.request.InvoiceCommunicationRequest;
import com.intelliatech.app.dto.request.InvoiceReminderRequest;
import com.intelliatech.app.dto.request.RecordInvoicePaymentRequest;
import com.intelliatech.app.dto.request.ReverseInvoicePaymentRequest;
import com.intelliatech.app.dto.request.VoidInvoiceRequest;
import com.intelliatech.app.dto.response.InvoiceActionPermissionsResponse;
import com.intelliatech.app.dto.response.EligibleInvoiceForCreditNoteResponse;
import com.intelliatech.app.dto.response.InvoiceCommunicationResponse;
import com.intelliatech.app.dto.response.InvoiceCreditNoteLinkResponse;
import com.intelliatech.app.dto.response.InvoiceLifecycleResponse;
import com.intelliatech.app.dto.response.InvoicePaymentResponse;
import com.intelliatech.app.dto.response.InvoiceReminderResponse;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.InvoiceCommunication;
import com.intelliatech.app.entity.InvoiceCounter;
import com.intelliatech.app.entity.InvoiceCreditAllocation;
import com.intelliatech.app.entity.InvoiceCreditNoteLink;
import com.intelliatech.app.entity.InvoiceLifecycle;
import com.intelliatech.app.entity.InvoicePayment;
import com.intelliatech.app.entity.InvoicePaymentAllocation;
import com.intelliatech.app.entity.InvoiceReminder;
import com.intelliatech.app.entity.InvoiceStatus;
import com.intelliatech.app.entity.InvoiceTdsDeduction;
import com.intelliatech.app.entity.PaymentReceipt;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.InvoiceCommunicationRepository;
import com.intelliatech.app.repository.InvoiceCounterRepository;
import com.intelliatech.app.repository.InvoiceCreditAllocationRepository;
import com.intelliatech.app.repository.InvoiceCreditNoteLinkRepository;
import com.intelliatech.app.repository.InvoiceLifecycleRepository;
import com.intelliatech.app.repository.InvoicePaymentAllocationRepository;
import com.intelliatech.app.repository.InvoicePaymentRepository;
import com.intelliatech.app.repository.InvoiceReminderRepository;
import com.intelliatech.app.repository.InvoiceTdsDeductionRepository;
import com.intelliatech.app.repository.EInvoiceDetailRepository;
import com.intelliatech.app.repository.PaymentReceiptRepository;
import com.intelliatech.app.service.InvoiceLifecycleService;
import com.intelliatech.app.service.BankAccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InvoiceLifecycleServiceImpl implements InvoiceLifecycleService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Set<InvoiceStatus> PAYMENT_STATUSES = Set.of(
            InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE
    );
    private static final Set<InvoiceStatus> REMINDER_STATUSES = Set.of(
            InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE
    );
    private static final Set<InvoiceStatus> CREDIT_NOTE_STATUSES = Set.of(
            InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE
    );

    private final BusinessRecordRepository businessRecordRepository;
    private final InvoiceLifecycleRepository lifecycleRepository;
    private final InvoiceCounterRepository counterRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final InvoicePaymentAllocationRepository allocationRepository;
    private final InvoiceTdsDeductionRepository tdsRepository;
    private final PaymentReceiptRepository receiptRepository;
    private final InvoiceCreditAllocationRepository creditAllocationRepository;
    private final InvoiceCreditNoteLinkRepository creditNoteLinkRepository;
    private final InvoiceReminderRepository reminderRepository;
    private final InvoiceCommunicationRepository communicationRepository;
    private final ObjectMapper objectMapper;
    private final BankAccountService bankAccountService;
    private final EInvoiceDetailRepository eInvoiceDetailRepository;

    @Autowired
    public InvoiceLifecycleServiceImpl(BusinessRecordRepository businessRecordRepository,
            InvoiceLifecycleRepository lifecycleRepository, InvoiceCounterRepository counterRepository,
            InvoicePaymentRepository paymentRepository, InvoicePaymentAllocationRepository allocationRepository,
            InvoiceTdsDeductionRepository tdsRepository, PaymentReceiptRepository receiptRepository,
            InvoiceCreditAllocationRepository creditAllocationRepository,
            InvoiceCreditNoteLinkRepository creditNoteLinkRepository, InvoiceReminderRepository reminderRepository,
            InvoiceCommunicationRepository communicationRepository, ObjectMapper objectMapper,
            BankAccountService bankAccountService, EInvoiceDetailRepository eInvoiceDetailRepository) {
        this.businessRecordRepository = businessRecordRepository;
        this.lifecycleRepository = lifecycleRepository;
        this.counterRepository = counterRepository;
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.tdsRepository = tdsRepository;
        this.receiptRepository = receiptRepository;
        this.creditAllocationRepository = creditAllocationRepository;
        this.creditNoteLinkRepository = creditNoteLinkRepository;
        this.reminderRepository = reminderRepository;
        this.communicationRepository = communicationRepository;
        this.objectMapper = objectMapper;
        this.bankAccountService = bankAccountService;
        this.eInvoiceDetailRepository = eInvoiceDetailRepository;
    }

    InvoiceLifecycleServiceImpl(BusinessRecordRepository businessRecordRepository,
            InvoiceLifecycleRepository lifecycleRepository, InvoiceCounterRepository counterRepository,
            InvoicePaymentRepository paymentRepository, InvoicePaymentAllocationRepository allocationRepository,
            InvoiceTdsDeductionRepository tdsRepository, PaymentReceiptRepository receiptRepository,
            InvoiceCreditAllocationRepository creditAllocationRepository,
            InvoiceCreditNoteLinkRepository creditNoteLinkRepository, InvoiceReminderRepository reminderRepository,
            InvoiceCommunicationRepository communicationRepository, ObjectMapper objectMapper) {
        this(businessRecordRepository, lifecycleRepository, counterRepository, paymentRepository,
                allocationRepository, tdsRepository, receiptRepository, creditAllocationRepository,
                creditNoteLinkRepository, reminderRepository, communicationRepository, objectMapper, null, null);
    }

    @Override
    @Transactional
    public void initializeDraft(BusinessRecord invoice) {
        if (!isInvoice(invoice) || lifecycleRepository.findByInvoiceId(invoice.getId()).isPresent()) return;
        InvoiceLifecycle lifecycle = new InvoiceLifecycle();
        lifecycle.setInvoice(invoice);
        lifecycle.setStatus(InvoiceStatus.DRAFT);
        lifecycle.setBalanceDue(money(invoice.getAmount()));
        lifecycleRepository.save(lifecycle);
        synchronizeRecord(invoice, lifecycle);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse findByInvoiceId(Long invoiceId) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        recalculate(lifecycle);
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public Map<Long, InvoiceLifecycleResponse> findAllByInvoiceIds(Collection<Long> invoiceIds) {
        Map<Long, InvoiceLifecycleResponse> responses = new LinkedHashMap<>();
        if (invoiceIds == null || invoiceIds.isEmpty()) return responses;
        for (Long invoiceId : invoiceIds.stream().distinct().toList()) {
            InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
            recalculate(lifecycle);
            responses.put(invoiceId, response(lifecycle, false));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EligibleInvoiceForCreditNoteResponse> findEligibleForCreditNote(Long customerId, Long creditNoteId) {
        BusinessRecord customer = businessRecordRepository.findByModuleAndTypeAndId("sales", "customers", customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        JsonNode customerNotes = json(customer.getNotes());
        List<EligibleInvoiceForCreditNoteResponse> eligible = new ArrayList<>();
        for (BusinessRecord invoice : businessRecordRepository
                .findAllByModuleAndTypeAndPartyNameIgnoreCaseOrderByRecordDateDesc("sales", "invoices", customer.getPartyName())) {
            JsonNode invoiceNotes = json(invoice.getNotes());
            Long invoiceCustomerId = longValue(invoiceNotes, "Customer ID", "customerId");
            if (invoiceCustomerId != null && !customerId.equals(invoiceCustomerId)) continue;

            InvoiceLifecycle lifecycle = lifecycleRepository.findByInvoiceId(invoice.getId()).orElse(null);
            if (lifecycle == null || !CREDIT_NOTE_STATUSES.contains(lifecycle.getStatus())) continue;

            List<InvoiceCreditNoteLink> priorLinks = creditNoteLinkRepository.findAllByInvoiceId(invoice.getId()).stream()
                    .filter(link -> creditNoteId == null || link.getCreditNote() == null
                            || !creditNoteId.equals(link.getCreditNote().getId()))
                    .toList();
            BigDecimal previouslyCredited = priorLinks.stream()
                    .map(InvoiceCreditNoteLink::getAmountApplied)
                    .map(this::money)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal remaining = money(invoice.getAmount()).subtract(previouslyCredited).max(ZERO);
            if (remaining.signum() <= 0) continue;

            eligible.add(new EligibleInvoiceForCreditNoteResponse(
                    invoice.getId(),
                    customer.getId(),
                    invoice.getRecordNumber(),
                    invoice.getRecordDate(),
                    invoice.getDueDate(),
                    invoice.getPartyName(),
                    invoice.getPartyEmail(),
                    invoice.getPartyPhone(),
                    invoice.getPartyCity(),
                    text(customerNotes, "billingAddress", "Customer Billing Address"),
                    text(customerNotes, "country", "Customer Country"),
                    text(customerNotes, "state", "Customer Billing State", "placeOfSupply"),
                    text(invoiceNotes, "Currency", "currency"),
                    lifecycle.getStatus().displayName(),
                    money(invoice.getAmount()),
                    money(lifecycle.getBalanceDue()),
                    money(previouslyCredited),
                    money(remaining),
                    creditedQuantities(priorLinks),
                    invoice.getNotes()
            ));
        }
        return eligible;
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse markAsSent(Long invoiceId, String recipient) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        if (lifecycle.getStatus() == InvoiceStatus.PAID || lifecycle.getStatus() == InvoiceStatus.VOID) {
            throw new IllegalStateException("Paid or void invoices cannot be marked as sent.");
        }
        markSent(lifecycle, recipient);
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse markAsViewed(Long invoiceId) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        LocalDateTime now = LocalDateTime.now();
        if (lifecycle.getFirstViewedAt() == null) lifecycle.setFirstViewedAt(now);
        lifecycle.setLastViewedAt(now);
        if (lifecycle.getStatus() == InvoiceStatus.SENT) lifecycle.setStatus(InvoiceStatus.VIEWED);
        synchronizeRecord(lifecycle.getInvoice(), lifecycle);
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse recordCommunication(Long invoiceId, InvoiceCommunicationRequest request) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        if (!permissions(lifecycle).send()) throw new IllegalStateException("This invoice cannot be sent in its current status.");
        String type = requiredUpper(request.type(), "Communication type is required.");
        if (!Set.of("EMAIL", "SMS").contains(type)) throw new IllegalArgumentException("Communication type must be EMAIL or SMS.");
        if (!StringUtils.hasText(request.recipient())) throw new IllegalArgumentException("A recipient is required.");

        InvoiceCommunication communication = new InvoiceCommunication();
        communication.setInvoice(lifecycle.getInvoice());
        communication.setCommunicationType(type);
        communication.setRecipient(request.recipient().trim());
        communication.setCc(trimToNull(request.cc()));
        communication.setBcc(trimToNull(request.bcc()));
        communication.setSubject(trimToNull(request.subject()));
        communication.setBody(trimToNull(request.body()));
        communication.setScheduledAt(request.scheduledAt());
        communication.setCreatedBy(currentUser());

        if (request.scheduledAt() != null && request.scheduledAt().isAfter(LocalDateTime.now())) {
            communication.setDeliveryStatus("QUEUED");
        } else if (Boolean.TRUE.equals(request.deliverySuccessful())) {
            communication.setDeliveryStatus("SENT");
            communication.setSentAt(LocalDateTime.now());
            markSent(lifecycle, request.recipient());
        } else {
            communication.setDeliveryStatus("FAILED");
            communication.setFailureReason(StringUtils.hasText(request.failureReason())
                    ? request.failureReason().trim() : "Delivery was not confirmed.");
        }
        communicationRepository.save(communication);
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse recordPayment(Long invoiceId, RecordInvoicePaymentRequest request) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        if (!PAYMENT_STATUSES.contains(lifecycle.getStatus())) {
            throw new IllegalStateException("Payments can only be recorded for sent, viewed, partially paid, or overdue invoices.");
        }
        if (!StringUtils.hasText(request.idempotencyKey())) {
            throw new IllegalArgumentException("Payment request key is required. Please retry the payment.");
        }
        var existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey().trim());
        if (existing.isPresent()) {
            if (!existing.get().getPrimaryInvoice().getId().equals(invoiceId)) {
                throw new DuplicateResourceException("This payment request was already used for another invoice.");
            }
            return response(lifecycle, true);
        }

        PaymentAmounts amounts = validatePayment(lifecycle.getInvoice(), lifecycle.getBalanceDue(), request);
        InvoicePayment payment = new InvoicePayment();
        applyPaymentFields(payment, lifecycle.getInvoice(), request, amounts);
        payment.setPaymentNumber(nextNumber("PAYMENT", "PAY"));
        payment.setIdempotencyKey(request.idempotencyKey().trim());
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("This payment was already submitted.");
        }

        InvoicePaymentAllocation allocation = new InvoicePaymentAllocation();
        allocation.setPayment(payment);
        allocation.setInvoice(lifecycle.getInvoice());
        allocation.setCashAmountApplied(amounts.grossAmount());
        allocation.setTdsAmountApplied(amounts.tdsAmount());
        allocation.setCreditAmountApplied(amounts.creditAmount());
        allocationRepository.save(allocation);
        synchronizePaymentCredit(payment, amounts.creditAmount());
        saveTds(payment, request, amounts);
        recalculate(lifecycle);
        saveReceipt(payment, lifecycle.getBalanceDue());
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse updatePayment(Long invoiceId, Long paymentId, RecordInvoicePaymentRequest request) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        InvoicePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));
        assertPaymentBelongsToInvoice(payment, invoiceId);
        if (Boolean.TRUE.equals(payment.getReconciled())) throw new IllegalStateException("Reconciled payments cannot be edited.");
        if (Boolean.TRUE.equals(payment.getReversed())) throw new IllegalStateException("Reversed payments cannot be edited.");
        InvoicePaymentAllocation allocation = allocationRepository.findByPaymentIdAndInvoiceId(paymentId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment allocation not found."));
        BigDecimal priorSettlement = money(allocation.getCashAmountApplied())
                .add(money(allocation.getTdsAmountApplied()))
                .add(money(allocation.getCreditAmountApplied()));
        BigDecimal eligibleOutstanding = money(lifecycle.getBalanceDue()).add(priorSettlement);
        PaymentAmounts amounts = validatePayment(lifecycle.getInvoice(), eligibleOutstanding, request);
        applyPaymentFields(payment, lifecycle.getInvoice(), request, amounts);
        paymentRepository.save(payment);
        allocation.setCashAmountApplied(amounts.grossAmount());
        allocation.setTdsAmountApplied(amounts.tdsAmount());
        allocation.setCreditAmountApplied(amounts.creditAmount());
        allocationRepository.save(allocation);
        synchronizePaymentCredit(payment, amounts.creditAmount());
        tdsRepository.findByPaymentId(paymentId).ifPresent(tdsRepository::delete);
        saveTds(payment, request, amounts);
        recalculate(lifecycle);
        saveReceipt(payment, lifecycle.getBalanceDue());
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse reversePayment(Long invoiceId, Long paymentId, ReverseInvoicePaymentRequest request) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        InvoicePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));
        assertPaymentBelongsToInvoice(payment, invoiceId);
        if (Boolean.TRUE.equals(payment.getReconciled())) throw new IllegalStateException("Reconciled payments cannot be reversed.");
        if (Boolean.TRUE.equals(payment.getReversed())) throw new IllegalStateException("This payment is already reversed.");
        payment.setReversed(true);
        payment.setReversedAt(LocalDateTime.now());
        payment.setReversalReason(StringUtils.hasText(request.reason()) ? request.reason().trim() : "Payment reversed");
        paymentRepository.save(payment);
        creditAllocationRepository.findByInvoiceIdAndSourceTypeAndSourceReference(invoiceId, "PAYMENT", payment.getPaymentNumber())
                .ifPresent(credit -> {
                    credit.setReversed(true);
                    creditAllocationRepository.save(credit);
                });
        recalculate(lifecycle);
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse deletePayment(Long invoiceId, Long paymentId) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        InvoicePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));
        assertPaymentBelongsToInvoice(payment, invoiceId);
        if (Boolean.TRUE.equals(payment.getReconciled())) {
            throw new IllegalStateException("Reconciled payments cannot be deleted.");
        }

        receiptRepository.findByPaymentId(paymentId).ifPresent(receiptRepository::delete);
        tdsRepository.findByPaymentId(paymentId).ifPresent(tdsRepository::delete);
        allocationRepository.findByPaymentIdAndInvoiceId(paymentId, invoiceId).ifPresent(allocationRepository::delete);
        creditAllocationRepository
                .findByInvoiceIdAndSourceTypeAndSourceReference(invoiceId, "PAYMENT", payment.getPaymentNumber())
                .ifPresent(creditAllocationRepository::delete);
        paymentRepository.delete(payment);

        receiptRepository.flush();
        tdsRepository.flush();
        allocationRepository.flush();
        creditAllocationRepository.flush();
        paymentRepository.flush();

        recalculate(lifecycle);
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse sendReminder(Long invoiceId, InvoiceReminderRequest request) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        if (!REMINDER_STATUSES.contains(lifecycle.getStatus())) {
            throw new IllegalStateException("Reminders are available only for open sent invoices.");
        }
        String channel = requiredUpper(request.channel(), "Reminder channel is required.");
        if (!Set.of("EMAIL", "SMS").contains(channel)) throw new IllegalArgumentException("Reminder channel must be EMAIL or SMS.");
        if (!StringUtils.hasText(request.recipient())) throw new IllegalArgumentException("A reminder recipient is required.");
        InvoiceReminder reminder = new InvoiceReminder();
        reminder.setInvoice(lifecycle.getInvoice());
        reminder.setChannel(channel);
        reminder.setRecipient(request.recipient().trim());
        reminder.setMessage(trimToNull(request.message()));
        reminder.setScheduledAt(request.scheduledAt());
        reminder.setCreatedBy(currentUser());
        if (request.scheduledAt() != null && request.scheduledAt().isAfter(LocalDateTime.now())) {
            reminder.setDeliveryStatus("QUEUED");
        } else if (Boolean.TRUE.equals(request.deliverySuccessful())) {
            reminder.setDeliveryStatus("SENT");
            reminder.setSentAt(LocalDateTime.now());
        } else {
            reminder.setDeliveryStatus("FAILED");
        }
        reminderRepository.save(reminder);
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse voidInvoice(Long invoiceId, VoidInvoiceRequest request) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        if (!permissions(lifecycle).voidInvoice()) throw new IllegalStateException("This invoice cannot be voided.");
        if (paymentRepository.countActiveByInvoiceId(invoiceId) > 0) {
            throw new IllegalStateException("Reverse all active payments before voiding this invoice.");
        }
        lifecycle.setStatus(InvoiceStatus.VOID);
        lifecycle.setVoidedAt(LocalDateTime.now());
        lifecycle.setVoidReason(trimToNull(request.reason()));
        lifecycle.setBalanceDue(ZERO);
        synchronizeRecord(lifecycle.getInvoice(), lifecycle);
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse applyCreditNote(Long invoiceId, Long creditNoteId, ApplyInvoiceCreditNoteRequest request) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        if (!CREDIT_NOTE_STATUSES.contains(lifecycle.getStatus())) {
            throw new IllegalStateException("A credit note cannot be applied to this invoice.");
        }
        BusinessRecord creditNote = businessRecordRepository.findByModuleAndTypeAndId("sales", "creditNotes", creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit note not found."));
        assertCreditNoteCustomer(lifecycle.getInvoice(), creditNote);
        BigDecimal amount = money(request.amount());
        if (amount.signum() <= 0) throw new IllegalArgumentException("Credit note amount must be greater than zero.");
        InvoiceCreditNoteLink link = creditNoteLinkRepository.findByInvoiceIdAndCreditNoteId(invoiceId, creditNoteId)
                .orElseGet(InvoiceCreditNoteLink::new);
        BigDecimal otherCreditNotes = creditNoteLinkRepository.findAllByInvoiceId(invoiceId).stream()
                .filter(existing -> existing.getId() == null || link.getId() == null || !existing.getId().equals(link.getId()))
                .map(InvoiceCreditNoteLink::getAmountApplied)
                .map(this::money)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal eligibleCredit = money(lifecycle.getInvoice().getAmount()).subtract(otherCreditNotes).max(ZERO);
        if (amount.compareTo(eligibleCredit) > 0) {
            throw new IllegalArgumentException("The credit note amount exceeds the remaining eligible invoice amount.");
        }
        assertCreditNoteQuantities(lifecycle.getInvoice(), creditNote, link);
        link.setInvoice(lifecycle.getInvoice());
        link.setCreditNote(creditNote);
        link.setAmountApplied(amount);
        creditNoteLinkRepository.save(link);
        synchronizeCreditNoteUsage(creditNote);
        recalculate(lifecycle);
        return response(lifecycle, true);
    }

    @Override
    @Transactional
    public InvoiceLifecycleResponse removeCreditNote(Long invoiceId, Long creditNoteId) {
        InvoiceLifecycle lifecycle = getOrCreateLocked(invoiceId);
        InvoiceCreditNoteLink link = creditNoteLinkRepository.findByInvoiceIdAndCreditNoteId(invoiceId, creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit note allocation not found."));
        BusinessRecord creditNote = link.getCreditNote();
        creditNoteLinkRepository.delete(link);
        creditNoteLinkRepository.flush();
        synchronizeCreditNoteUsage(creditNote);
        recalculate(lifecycle);
        return response(lifecycle, true);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanEdit(BusinessRecord invoice, BusinessRecordRequest request) {
        InvoiceLifecycle lifecycle = lifecycleRepository.findByInvoiceId(invoice.getId()).orElse(null);
        if (lifecycle == null) return;
        if (!permissions(lifecycle).edit()) throw new IllegalStateException("This invoice cannot be edited in its current status.");
        BigDecimal existingSettlement = money(lifecycle.getCashAmountPaid())
                .add(money(lifecycle.getTdsSettled()))
                .add(money(lifecycle.getCreditApplied()))
                .add(money(lifecycle.getCreditNoteApplied()));
        if (existingSettlement.signum() > 0 && money(invoice.getAmount()).compareTo(money(request.amount())) != 0) {
            throw new IllegalStateException("Invoice total cannot be changed after a payment has been recorded.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanDelete(Long invoiceId) {
        InvoiceLifecycle lifecycle = lifecycleRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice lifecycle not found."));
        if (lifecycle.getStatus() != InvoiceStatus.DRAFT) throw new IllegalStateException("Only draft invoices can be deleted.");
        if (paymentRepository.countActiveByInvoiceId(invoiceId) > 0) {
            throw new IllegalStateException("An invoice with payments cannot be deleted.");
        }
        if (creditAllocationRepository.countByInvoiceIdAndReversedFalse(invoiceId) > 0
                || creditNoteLinkRepository.countByInvoiceId(invoiceId) > 0) {
            throw new IllegalStateException("Remove invoice credits and credit notes before deleting this invoice.");
        }
    }

    @Override
    @Transactional
    public void refreshOverdueStatus(Long invoiceId) {
        recalculate(getOrCreateLocked(invoiceId));
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 5 1 * * *")
    public void refreshAllOverdueStatuses() {
        List<InvoiceStatus> statuses = List.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);
        lifecycleRepository.findAllByStatusIn(statuses).forEach(this::recalculate);
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${app.invoice.scheduler-delay-ms:60000}")
    public void processScheduledMessages() {
        LocalDateTime now = LocalDateTime.now();
        communicationRepository.findAllByDeliveryStatusAndScheduledAtLessThanEqual("QUEUED", now).forEach(communication -> {
            InvoiceLifecycle lifecycle = getOrCreateLocked(communication.getInvoice().getId());
            if (!permissions(lifecycle).send()) {
                communication.setDeliveryStatus("CANCELLED");
                communication.setFailureReason("Invoice status no longer allows sending.");
                return;
            }
            communication.setDeliveryStatus("SENT");
            communication.setSentAt(now);
            markSent(lifecycle, communication.getRecipient());
        });
        reminderRepository.findAllByDeliveryStatusAndScheduledAtLessThanEqual("QUEUED", now).forEach(reminder -> {
            InvoiceLifecycle lifecycle = getOrCreateLocked(reminder.getInvoice().getId());
            if (!permissions(lifecycle).reminder()) {
                reminder.setDeliveryStatus("CANCELLED");
                return;
            }
            reminder.setDeliveryStatus("SENT");
            reminder.setSentAt(now);
        });
    }

    private InvoiceLifecycle getOrCreateLocked(Long invoiceId) {
        return lifecycleRepository.findByInvoiceIdForUpdate(invoiceId).orElseGet(() -> {
            BusinessRecord invoice = invoice(invoiceId);
            InvoiceLifecycle lifecycle = new InvoiceLifecycle();
            lifecycle.setInvoice(invoice);
            lifecycle.setStatus(statusFrom(invoice.getStatus()));
            lifecycle.setBalanceDue(money(invoice.getBalanceAmount()));
            return lifecycleRepository.saveAndFlush(lifecycle);
        });
    }

    private BusinessRecord invoice(Long invoiceId) {
        return businessRecordRepository.findByModuleAndTypeAndId("sales", "invoices", invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found."));
    }

    private void markSent(InvoiceLifecycle lifecycle, String recipient) {
        if (lifecycle.getSentAt() == null) lifecycle.setSentAt(LocalDateTime.now());
        if (StringUtils.hasText(recipient)) lifecycle.setSentRecipient(recipient.trim());
        if (lifecycle.getStatus() == InvoiceStatus.DRAFT) lifecycle.setStatus(InvoiceStatus.SENT);
        synchronizeRecord(lifecycle.getInvoice(), lifecycle);
    }

    private PaymentAmounts validatePayment(BusinessRecord invoice, BigDecimal eligibleOutstanding, RecordInvoicePaymentRequest request) {
        if (request.paymentDate() == null) throw new IllegalArgumentException("Payment date is required.");
        BigDecimal gross = money(request.amountReceived());
        BigDecimal charges = money(request.bankCharges());
        BigDecimal credit = money(request.creditApplied());
        if (gross.signum() < 0) throw new IllegalArgumentException("Amount received cannot be negative.");
        if (charges.signum() < 0) throw new IllegalArgumentException("Bank charges cannot be negative.");
        if (charges.compareTo(gross) > 0) throw new IllegalArgumentException("Bank charges cannot exceed the amount received.");
        if (credit.signum() < 0) throw new IllegalArgumentException("Credit applied cannot be negative.");
        if (gross.signum() > 0) {
            if (!StringUtils.hasText(request.paymentMode())) throw new IllegalArgumentException("Payment mode is required.");
            if (requiresDepositAccount(request.paymentMode()) && request.bankAccountId() == null && !StringUtils.hasText(request.depositAccount())) {
                throw new IllegalArgumentException("Deposit To / Bank Account is required for this payment mode.");
            }
        }

        BigDecimal tds = ZERO;
        BigDecimal tdsRate = money4(request.tdsPercentage());
        String tdsBaseType = configuredTdsBaseType();
        BigDecimal tdsBase = tdsBase(invoice, tdsBaseType);
        if (Boolean.TRUE.equals(request.tdsDeducted())) {
            if (tdsRate.signum() < 0 || tdsRate.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("TDS percentage must be between 0 and 100.");
            }
            if (request.tdsAmount() != null) tds = money(request.tdsAmount());
            else if (tdsRate.signum() > 0) tds = money(tdsBase.multiply(tdsRate).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            if (tds.signum() < 0) throw new IllegalArgumentException("TDS amount cannot be negative.");
            if (tds.compareTo(tdsBase) > 0) throw new IllegalArgumentException("TDS amount cannot exceed the eligible TDS base.");
        } else {
            tdsRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal settlement = gross.add(tds).add(credit);
        if (settlement.signum() <= 0) {
            throw new IllegalArgumentException("Enter an amount received, TDS amount, or credit applied.");
        }
        if (settlement.compareTo(money(eligibleOutstanding)) > 0) {
            throw new IllegalArgumentException("Total settlement cannot exceed the outstanding amount.");
        }
        return new PaymentAmounts(gross, charges, money(gross.subtract(charges)), tds, credit, tdsBaseType, tdsBase, tdsRate);
    }

    private void applyPaymentFields(InvoicePayment payment, BusinessRecord invoice, RecordInvoicePaymentRequest request, PaymentAmounts amounts) {
        payment.setPrimaryInvoice(invoice);
        payment.setCustomerName(invoice.getPartyName());
        payment.setCurrencyCode(currencyCode(invoice));
        payment.setPaymentDate(request.paymentDate());
        payment.setPaymentMode(trimToNull(request.paymentMode()));
        Long retainedBankAccountId = payment.getBankAccount() == null ? null : payment.getBankAccount().getId();
        var bankAccount = bankAccountService == null ? null : bankAccountService.selection(request.bankAccountId(), retainedBankAccountId);
        payment.setBankAccount(bankAccount);
        payment.setDepositAccount(bankAccount == null ? trimToNull(request.depositAccount()) : bankAccount.getAccountName());
        payment.setReferenceNumber(trimToNull(request.referenceNumber()));
        payment.setGrossAmountReceived(amounts.grossAmount());
        payment.setBankCharges(amounts.bankCharges());
        payment.setNetBankCredit(amounts.netBankCredit());
        payment.setNotes(trimToNull(request.notes()));
        payment.setAttachmentUrl(trimToNull(request.attachmentUrl()));
        payment.setSendThankYouEmail(Boolean.TRUE.equals(request.sendThankYouEmail()));
        if (payment.getCreatedBy() == null) payment.setCreatedBy(currentUser());
    }

    private void saveTds(InvoicePayment payment, RecordInvoicePaymentRequest request, PaymentAmounts amounts) {
        if (amounts.tdsAmount().signum() <= 0) return;
        InvoiceTdsDeduction tds = new InvoiceTdsDeduction();
        tds.setPayment(payment);
        tds.setBaseType(amounts.tdsBaseType());
        tds.setBaseAmount(amounts.tdsBase());
        tds.setPercentage(amounts.tdsRate());
        tds.setAmount(amounts.tdsAmount());
        tds.setSectionCode(trimToNull(request.tdsSectionCode()));
        tds.setCertificateNumber(trimToNull(request.tdsCertificateNumber()));
        tds.setCertificateDate(request.tdsCertificateDate());
        tds.setRemarks(trimToNull(request.tdsRemarks()));
        tdsRepository.save(tds);
    }

    private void synchronizePaymentCredit(InvoicePayment payment, BigDecimal amount) {
        InvoiceCreditAllocation allocation = creditAllocationRepository
                .findByInvoiceIdAndSourceTypeAndSourceReference(payment.getPrimaryInvoice().getId(), "PAYMENT", payment.getPaymentNumber())
                .orElseGet(InvoiceCreditAllocation::new);
        if (money(amount).signum() <= 0 && allocation.getId() == null) return;
        allocation.setInvoice(payment.getPrimaryInvoice());
        allocation.setSourceType("PAYMENT");
        allocation.setSourceReference(payment.getPaymentNumber());
        allocation.setAmount(money(amount));
        allocation.setAppliedOn(payment.getPaymentDate());
        allocation.setReversed(money(amount).signum() <= 0);
        creditAllocationRepository.save(allocation);
    }

    private void saveReceipt(InvoicePayment payment, BigDecimal remainingBalance) {
        PaymentReceipt receipt = receiptRepository.findByPaymentId(payment.getId()).orElseGet(PaymentReceipt::new);
        receipt.setPayment(payment);
        if (!StringUtils.hasText(receipt.getReceiptNumber())) receipt.setReceiptNumber(nextNumber("RECEIPT", "RCP"));
        receipt.setRemainingBalance(money(remainingBalance));
        receiptRepository.save(receipt);
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

    private void recalculate(InvoiceLifecycle lifecycle) {
        if (lifecycle.getStatus() == InvoiceStatus.VOID) {
            synchronizeRecord(lifecycle.getInvoice(), lifecycle);
            return;
        }
        BigDecimal cash = ZERO;
        BigDecimal tds = ZERO;
        for (InvoicePaymentAllocation allocation : allocationRepository.findActiveByInvoiceId(lifecycle.getInvoice().getId())) {
            cash = cash.add(money(allocation.getCashAmountApplied()));
            tds = tds.add(money(allocation.getTdsAmountApplied()));
        }
        BigDecimal directCredits = creditAllocationRepository.findAllByInvoiceIdAndReversedFalse(lifecycle.getInvoice().getId()).stream()
                .map(InvoiceCreditAllocation::getAmount).map(this::money).reduce(ZERO, BigDecimal::add);
        BigDecimal linkedCreditNotes = creditNoteLinkRepository.findAllByInvoiceId(lifecycle.getInvoice().getId()).stream()
                .map(InvoiceCreditNoteLink::getAmountApplied).map(this::money).reduce(ZERO, BigDecimal::add);
        BigDecimal credits = money(directCredits);
        BigDecimal total = money(lifecycle.getInvoice().getAmount());
        BigDecimal settlementBeforeCreditNotes = money(cash.add(tds).add(credits));
        BigDecimal creditNotes = money(linkedCreditNotes.min(total.subtract(settlementBeforeCreditNotes).max(ZERO)));
        BigDecimal settled = money(cash.add(tds).add(credits).add(creditNotes));
        BigDecimal balance = money(total.subtract(settled).max(BigDecimal.ZERO));
        lifecycle.setCashAmountPaid(money(cash));
        lifecycle.setTdsSettled(money(tds));
        lifecycle.setCreditApplied(credits);
        lifecycle.setCreditNoteApplied(money(creditNotes));
        lifecycle.setBalanceDue(balance);

        boolean overdue = lifecycle.getInvoice().getDueDate() != null
                && LocalDate.now().isAfter(lifecycle.getInvoice().getDueDate()) && balance.signum() > 0;
        if (balance.signum() == 0 && total.signum() > 0) {
            lifecycle.setStatus(InvoiceStatus.PAID);
            if (lifecycle.getPaidAt() == null) lifecycle.setPaidAt(LocalDateTime.now());
        } else {
            lifecycle.setPaidAt(null);
            if (overdue && lifecycle.getStatus() != InvoiceStatus.DRAFT) lifecycle.setStatus(InvoiceStatus.OVERDUE);
            else if (settled.signum() > 0) lifecycle.setStatus(InvoiceStatus.PARTIALLY_PAID);
            else if (lifecycle.getFirstViewedAt() != null) lifecycle.setStatus(InvoiceStatus.VIEWED);
            else if (lifecycle.getSentAt() != null) lifecycle.setStatus(InvoiceStatus.SENT);
            else lifecycle.setStatus(InvoiceStatus.DRAFT);
        }
        synchronizeRecord(lifecycle.getInvoice(), lifecycle);
    }

    private void synchronizeRecord(BusinessRecord invoice, InvoiceLifecycle lifecycle) {
        invoice.setStatus(lifecycle.getStatus().displayName());
        invoice.setBalanceAmount(money(lifecycle.getBalanceDue()));
        String secondaryStatus = "Unpaid";
        if (lifecycle.getStatus() == InvoiceStatus.PAID) secondaryStatus = "Paid";
        else if (lifecycle.getStatus() == InvoiceStatus.VOID) secondaryStatus = "Void";
        else if ((lifecycle.getStatus() == InvoiceStatus.PARTIALLY_PAID || lifecycle.getStatus() == InvoiceStatus.OVERDUE)
                && lifecycle.getCashAmountPaid().add(lifecycle.getTdsSettled()).add(lifecycle.getCreditApplied())
                .add(lifecycle.getCreditNoteApplied()).signum() > 0) {
            secondaryStatus = "Partially Paid";
        }
        invoice.setSecondaryStatus(secondaryStatus);
        businessRecordRepository.save(invoice);
        lifecycleRepository.save(lifecycle);
    }

    private InvoiceLifecycleResponse response(InvoiceLifecycle lifecycle, boolean includeHistory) {
        BusinessRecord invoice = lifecycle.getInvoice();
        BigDecimal totalSettled = money(lifecycle.getCashAmountPaid())
                .add(money(lifecycle.getTdsSettled()))
                .add(money(lifecycle.getCreditApplied()))
                .add(money(lifecycle.getCreditNoteApplied()));
        long overdueDays = invoice.getDueDate() != null && lifecycle.getBalanceDue().signum() > 0 && LocalDate.now().isAfter(invoice.getDueDate())
                ? ChronoUnit.DAYS.between(invoice.getDueDate(), LocalDate.now()) : 0;
        return new InvoiceLifecycleResponse(
                invoice.getId(), invoice.getRecordNumber(), invoice.getPartyName(), invoice.getRecordDate(), invoice.getDueDate(),
                currency(invoice), lifecycle.getStatus().displayName(), money(invoice.getAmount()), money(lifecycle.getCashAmountPaid()),
                money(lifecycle.getTdsSettled()), money(lifecycle.getCreditApplied()), money(lifecycle.getCreditNoteApplied()),
                money(totalSettled), money(lifecycle.getBalanceDue()), lifecycle.getSentAt(), lifecycle.getSentRecipient(),
                lifecycle.getFirstViewedAt(), lifecycle.getLastViewedAt(), lifecycle.getPaidAt(), lifecycle.getVoidedAt(),
                lifecycle.getVoidReason(), overdueDays, permissions(lifecycle),
                includeHistory ? paymentResponses(invoice.getId()) : List.of(),
                includeHistory ? reminderResponses(invoice.getId()) : List.of(),
                includeHistory ? communicationResponses(invoice.getId()) : List.of(),
                includeHistory ? creditNoteResponses(invoice.getId()) : List.of()
        );
    }

    private List<InvoicePaymentResponse> paymentResponses(Long invoiceId) {
        return allocationRepository.findHistoryByInvoiceId(invoiceId).stream().map(allocation -> {
            InvoicePayment payment = allocation.getPayment();
            InvoiceTdsDeduction tds = tdsRepository.findByPaymentId(payment.getId()).orElse(null);
            PaymentReceipt receipt = receiptRepository.findByPaymentId(payment.getId()).orElse(null);
            return new InvoicePaymentResponse(
                    payment.getId(), payment.getPaymentNumber(), receipt == null ? "" : receipt.getReceiptNumber(),
                    receipt == null ? ZERO : money(receipt.getRemainingBalance()), payment.getCurrencyCode(), payment.getPaymentDate(), payment.getPaymentMode(),
                    payment.getDepositAccount(), payment.getBankAccount() == null ? null : payment.getBankAccount().getId(), payment.getReferenceNumber(), Boolean.TRUE.equals(payment.getReversed()) ? "Reversed" : "Paid",
                    money(allocation.getCashAmountApplied()),
                    money(allocation.getTdsAmountApplied()),
                    money(allocation.getCreditAmountApplied()), money(payment.getBankCharges()),
                    money(payment.getNetBankCredit()), tds == null ? null : tds.getBaseType(), tds == null ? ZERO : money(tds.getBaseAmount()),
                    tds == null ? BigDecimal.ZERO : tds.getPercentage(), tds == null ? null : tds.getSectionCode(),
                    tds == null ? null : tds.getCertificateNumber(), tds == null ? null : tds.getCertificateDate(),
                    tds == null ? null : tds.getRemarks(), payment.getNotes(), payment.getAttachmentUrl(),
                    Boolean.TRUE.equals(payment.getSendThankYouEmail()), Boolean.TRUE.equals(payment.getReconciled()),
                    Boolean.TRUE.equals(payment.getReversed()), payment.getReversedAt(), payment.getReversalReason(),
                    payment.getCreatedBy(), payment.getCreatedAt()
            );
        }).toList();
    }

    private List<InvoiceReminderResponse> reminderResponses(Long invoiceId) {
        return reminderRepository.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .map(reminder -> new InvoiceReminderResponse(reminder.getId(), reminder.getChannel(), reminder.getRecipient(), reminder.getMessage(),
                        reminder.getScheduledAt(), reminder.getSentAt(), reminder.getDeliveryStatus(), reminder.getCreatedBy(), reminder.getCreatedAt()))
                .toList();
    }

    private List<InvoiceCommunicationResponse> communicationResponses(Long invoiceId) {
        return communicationRepository.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .map(communication -> new InvoiceCommunicationResponse(communication.getId(), communication.getCommunicationType(),
                        communication.getRecipient(), communication.getCc(), communication.getBcc(), communication.getSubject(), communication.getBody(),
                        communication.getScheduledAt(), communication.getSentAt(), communication.getDeliveryStatus(), communication.getFailureReason(),
                        communication.getCreatedBy(), communication.getCreatedAt()))
                .toList();
    }

    private List<InvoiceCreditNoteLinkResponse> creditNoteResponses(Long invoiceId) {
        return creditNoteLinkRepository.findAllByInvoiceId(invoiceId).stream()
                .map(link -> {
                    BusinessRecord creditNote = link.getCreditNote();
                    String eInvoiceStatus = eInvoiceDetailRepository == null ? "NOT_GENERATED"
                            : eInvoiceDetailRepository.findByCreditNoteId(creditNote.getId())
                            .map(detail -> detail.getStatus().name()).orElse("NOT_GENERATED");
                    return new InvoiceCreditNoteLinkResponse(link.getId(), creditNote.getId(), creditNote.getRecordNumber(),
                            creditNote.getRecordDate(), money(creditNote.getAmount()), eInvoiceStatus,
                            money(link.getAmountApplied()), money(creditNote.getBalanceAmount()), link.getCreatedAt());
                })
                .toList();
    }

    private InvoiceActionPermissionsResponse permissions(InvoiceLifecycle lifecycle) {
        InvoiceStatus status = lifecycle.getStatus();
        boolean activePayments = paymentRepository.countActiveByInvoiceId(lifecycle.getInvoice().getId()) > 0;
        return switch (status) {
            case DRAFT -> new InvoiceActionPermissionsResponse(true, true, false, false, false, false, !activePayments, true);
            case SENT, VIEWED -> new InvoiceActionPermissionsResponse(true, true, true, true, true, !activePayments, false, true);
            case PARTIALLY_PAID -> new InvoiceActionPermissionsResponse(true, true, true, true, true, false, false, true);
            case PAID -> new InvoiceActionPermissionsResponse(false, false, false, false, true, false, false, true);
            case OVERDUE -> new InvoiceActionPermissionsResponse(true, true, true, true, true, !activePayments, false, true);
            case VOID -> new InvoiceActionPermissionsResponse(false, false, false, false, false, false, false, false);
        };
    }

    private void assertCreditNoteCustomer(BusinessRecord invoice, BusinessRecord creditNote) {
        JsonNode creditNotes = json(creditNote.getNotes());
        Long sourceInvoiceId = longValue(creditNotes, "Source Invoice ID", "convertedFromId");
        if (sourceInvoiceId != null && !invoice.getId().equals(sourceInvoiceId)) {
            throw new IllegalArgumentException("The selected invoice does not belong to this credit note.");
        }
        Long invoiceCustomerId = longValue(json(invoice.getNotes()), "Customer ID", "customerId");
        Long creditCustomerId = longValue(creditNotes, "Customer ID", "customerId");
        if (invoiceCustomerId != null && creditCustomerId != null && !invoiceCustomerId.equals(creditCustomerId)) {
            throw new IllegalArgumentException("The selected invoice does not belong to this customer.");
        }
        if (!invoice.getPartyName().equalsIgnoreCase(creditNote.getPartyName())) {
            throw new IllegalArgumentException("The selected invoice does not belong to this customer.");
        }
    }

    private void assertCreditNoteQuantities(BusinessRecord invoice, BusinessRecord creditNote, InvoiceCreditNoteLink currentLink) {
        Map<String, BigDecimal> invoiceQuantities = itemQuantities(invoice);
        Map<String, BigDecimal> requestedQuantities = itemQuantities(creditNote);
        if (requestedQuantities.isEmpty() || invoiceQuantities.isEmpty()) return;
        List<InvoiceCreditNoteLink> priorLinks = creditNoteLinkRepository.findAllByInvoiceId(invoice.getId()).stream()
                .filter(existing -> currentLink.getId() == null || existing.getId() == null
                        || !currentLink.getId().equals(existing.getId()))
                .toList();
        Map<String, BigDecimal> priorQuantities = creditedQuantities(priorLinks);
        for (Map.Entry<String, BigDecimal> requested : requestedQuantities.entrySet()) {
            BigDecimal invoiced = invoiceQuantities.getOrDefault(requested.getKey(), ZERO);
            BigDecimal alreadyCredited = priorQuantities.getOrDefault(requested.getKey(), ZERO);
            BigDecimal remaining = invoiced.subtract(alreadyCredited).max(ZERO);
            if (requested.getValue().compareTo(remaining) > 0) {
                throw new IllegalArgumentException("The credit quantity exceeds the remaining invoice-item quantity.");
            }
        }
    }

    private void synchronizeCreditNoteUsage(BusinessRecord creditNote) {
        BigDecimal total = money(creditNote.getAmount());
        BigDecimal used = creditNoteLinkRepository.findAllByCreditNoteId(creditNote.getId()).stream()
                .map(InvoiceCreditNoteLink::getAmountApplied)
                .map(this::money)
                .reduce(ZERO, BigDecimal::add)
                .min(total);
        BigDecimal remaining = money(total.subtract(used).max(ZERO));
        creditNote.setBalanceAmount(remaining);
        creditNote.setStatus(remaining.signum() == 0 && total.signum() > 0
                ? "Closed"
                : used.signum() > 0 ? "Used" : "Unused");
        businessRecordRepository.save(creditNote);
    }

    private Map<String, BigDecimal> creditedQuantities(List<InvoiceCreditNoteLink> links) {
        Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        for (InvoiceCreditNoteLink link : links) {
            if (link.getCreditNote() == null) continue;
            itemQuantities(link.getCreditNote()).forEach((key, quantity) -> quantities.merge(key, quantity, BigDecimal::add));
        }
        return quantities;
    }

    private Map<String, BigDecimal> itemQuantities(BusinessRecord record) {
        Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        JsonNode root = json(record.getNotes());
        JsonNode items = root.path("items");
        if (!items.isArray()) items = root.path("Invoice Items");
        if (!items.isArray()) items = root.path("Credit Note Items");
        if (!items.isArray()) return quantities;
        for (JsonNode item : items) {
            String key = text(item, "itemMasterId", "itemId", "itemKey", "itemNumber", "itemName");
            if (!StringUtils.hasText(key)) continue;
            BigDecimal quantity = decimal(item.path("quantity"));
            if (quantity.signum() > 0) quantities.merge(key.trim().toLowerCase(Locale.ROOT), quantity, BigDecimal::add);
        }
        return quantities;
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
        if (node == null) return "";
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
            return money(node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText("0").replace(",", "").trim()));
        } catch (NumberFormatException ignored) {
            return ZERO;
        }
    }

    private String configuredTdsBaseType() {
        return businessRecordRepository.findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization")
                .map(record -> {
                    try {
                        JsonNode notes = objectMapper.readTree(record.getNotes());
                        return notes.path("tdsBaseType").asText("TAXABLE_VALUE");
                    } catch (Exception ignored) {
                        return "TAXABLE_VALUE";
                    }
                })
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> Set.of("INVOICE_TOTAL", "TAXABLE_VALUE").contains(value))
                .orElse("TAXABLE_VALUE");
    }

    private BigDecimal tdsBase(BusinessRecord invoice, String baseType) {
        if (!"TAXABLE_VALUE".equals(baseType)) return money(invoice.getAmount());
        try {
            JsonNode root = objectMapper.readTree(invoice.getNotes());
            JsonNode totals = root.path("totals");
            if (totals.isMissingNode()) totals = root.path("Invoice Totals");
            JsonNode taxableAmount = totals.path("taxableAmount");
            BigDecimal value = taxableAmount.isNumber()
                    ? taxableAmount.decimalValue()
                    : new BigDecimal(taxableAmount.asText("0").replace(",", "").trim());
            return value.signum() > 0 ? money(value) : money(invoice.getAmount());
        } catch (Exception ignored) {
            return money(invoice.getAmount());
        }
    }

    private String currency(BusinessRecord invoice) {
        try {
            JsonNode notes = objectMapper.readTree(invoice.getNotes());
            String currency = notes.path("Currency").asText();
            return StringUtils.hasText(currency) ? currency : "INR - Indian Rupee";
        } catch (Exception ignored) {
            return "INR - Indian Rupee";
        }
    }

    private String currencyCode(BusinessRecord invoice) {
        String value = currency(invoice).trim().toUpperCase(Locale.ROOT);
        return value.length() >= 3 && value.substring(0, 3).matches("[A-Z]{3}") ? value.substring(0, 3) : "INR";
    }

    private InvoiceStatus statusFrom(String status) {
        if (!StringUtils.hasText(status)) return InvoiceStatus.DRAFT;
        try {
            return InvoiceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return InvoiceStatus.DRAFT;
        }
    }

    private boolean requiresDepositAccount(String paymentMode) {
        if (!StringUtils.hasText(paymentMode)) return false;
        return !Set.of("CASH", "OTHER").contains(paymentMode.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    private void assertPaymentBelongsToInvoice(InvoicePayment payment, Long invoiceId) {
        if (!payment.getPrimaryInvoice().getId().equals(invoiceId)) throw new ResourceNotFoundException("Payment not found for this invoice.");
    }

    private boolean isInvoice(BusinessRecord record) {
        return record != null && "sales".equals(record.getModule()) && "invoices".equals(record.getType());
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money4(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private String requiredUpper(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
        return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && StringUtils.hasText(authentication.getName()) ? authentication.getName() : "system";
    }

    private record PaymentAmounts(
            BigDecimal grossAmount,
            BigDecimal bankCharges,
            BigDecimal netBankCredit,
            BigDecimal tdsAmount,
            BigDecimal creditAmount,
            String tdsBaseType,
            BigDecimal tdsBase,
            BigDecimal tdsRate
    ) {
    }
}
