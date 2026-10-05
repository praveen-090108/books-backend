package com.intelliatech.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.RecordInvoicePaymentRequest;
import com.intelliatech.app.dto.request.ReverseInvoicePaymentRequest;
import com.intelliatech.app.dto.request.VoidInvoiceRequest;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.InvoiceCounter;
import com.intelliatech.app.entity.InvoiceCreditAllocation;
import com.intelliatech.app.entity.InvoiceCreditNoteLink;
import com.intelliatech.app.entity.InvoiceLifecycle;
import com.intelliatech.app.entity.InvoicePayment;
import com.intelliatech.app.entity.InvoicePaymentAllocation;
import com.intelliatech.app.entity.InvoiceStatus;
import com.intelliatech.app.entity.InvoiceTdsDeduction;
import com.intelliatech.app.entity.PaymentReceipt;
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
import com.intelliatech.app.repository.PaymentReceiptRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceLifecycleServiceImplTest {

    @Mock private BusinessRecordRepository businessRecordRepository;
    @Mock private InvoiceLifecycleRepository lifecycleRepository;
    @Mock private InvoiceCounterRepository counterRepository;
    @Mock private InvoicePaymentRepository paymentRepository;
    @Mock private InvoicePaymentAllocationRepository allocationRepository;
    @Mock private InvoiceTdsDeductionRepository tdsRepository;
    @Mock private PaymentReceiptRepository receiptRepository;
    @Mock private InvoiceCreditAllocationRepository creditAllocationRepository;
    @Mock private InvoiceCreditNoteLinkRepository creditNoteLinkRepository;
    @Mock private InvoiceReminderRepository reminderRepository;
    @Mock private InvoiceCommunicationRepository communicationRepository;

    private final List<InvoicePayment> payments = new ArrayList<>();
    private final List<InvoicePaymentAllocation> allocations = new ArrayList<>();
    private final List<InvoiceTdsDeduction> deductions = new ArrayList<>();
    private final List<InvoiceCreditAllocation> creditAllocations = new ArrayList<>();
    private final List<InvoiceCreditNoteLink> creditNoteLinks = new ArrayList<>();
    private final List<PaymentReceipt> receipts = new ArrayList<>();
    private final Map<String, InvoiceCounter> counters = new HashMap<>();
    private final AtomicLong paymentIds = new AtomicLong(1);
    private InvoiceLifecycleServiceImpl service;
    private InvoiceLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        BusinessRecord invoice = new BusinessRecord();
        invoice.setId(41L);
        invoice.setModule("sales");
        invoice.setType("invoices");
        invoice.setRecordNumber("INV-2026-0041");
        invoice.setPartyName("ABC Corporation");
        invoice.setAmount(new BigDecimal("100000.00"));
        invoice.setBalanceAmount(new BigDecimal("100000.00"));
        invoice.setRecordDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(15));
        invoice.setStatus("Sent");
        invoice.setSecondaryStatus("Unpaid");
        invoice.setNotes("{\"Currency\":\"INR - Indian Rupee\",\"totals\":{\"taxableAmount\":\"84745.76\"}}");

        lifecycle = new InvoiceLifecycle();
        lifecycle.setId(51L);
        lifecycle.setInvoice(invoice);
        lifecycle.setStatus(InvoiceStatus.SENT);
        lifecycle.setSentAt(LocalDateTime.now().minusDays(1));
        lifecycle.setBalanceDue(invoice.getAmount());

        service = new InvoiceLifecycleServiceImpl(
                businessRecordRepository, lifecycleRepository, counterRepository, paymentRepository,
                allocationRepository, tdsRepository, receiptRepository, creditAllocationRepository,
                creditNoteLinkRepository, reminderRepository, communicationRepository, new ObjectMapper()
        );

        when(lifecycleRepository.findByInvoiceIdForUpdate(41L)).thenReturn(Optional.of(lifecycle));
        when(lifecycleRepository.save(any(InvoiceLifecycle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(businessRecordRepository.save(any(BusinessRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(businessRecordRepository.findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization"))
                .thenReturn(Optional.empty());

        when(counterRepository.findByKeyForUpdate(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return Optional.of(counters.computeIfAbsent(key, ignored -> {
                InvoiceCounter counter = new InvoiceCounter();
                counter.setKey(key);
                counter.setNextValue(1L);
                return counter;
            }));
        });
        when(counterRepository.save(any(InvoiceCounter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(paymentRepository.findByIdempotencyKey(anyString())).thenAnswer(invocation -> payments.stream()
                .filter(payment -> invocation.getArgument(0).equals(payment.getIdempotencyKey())).findFirst());
        when(paymentRepository.saveAndFlush(any(InvoicePayment.class))).thenAnswer(invocation -> {
            InvoicePayment payment = invocation.getArgument(0);
            if (payment.getId() == null) payment.setId(paymentIds.getAndIncrement());
            if (!payments.contains(payment)) payments.add(payment);
            return payment;
        });
        when(paymentRepository.save(any(InvoicePayment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findById(anyLong())).thenAnswer(invocation -> payments.stream()
                .filter(payment -> payment.getId().equals(invocation.getArgument(0))).findFirst());
        when(paymentRepository.findAllByPrimaryInvoiceIdOrderByCreatedAtDesc(41L)).thenAnswer(ignored -> List.copyOf(payments));
        when(paymentRepository.countByPrimaryInvoiceIdAndReversedFalse(41L)).thenAnswer(ignored -> payments.stream()
                .filter(payment -> !Boolean.TRUE.equals(payment.getReversed())).count());
        doAnswer(invocation -> { payments.remove(invocation.getArgument(0)); return null; })
                .when(paymentRepository).delete(any(InvoicePayment.class));

        when(allocationRepository.save(any(InvoicePaymentAllocation.class))).thenAnswer(invocation -> {
            InvoicePaymentAllocation allocation = invocation.getArgument(0);
            if (!allocations.contains(allocation)) allocations.add(allocation);
            return allocation;
        });
        when(allocationRepository.findActiveByInvoiceId(41L)).thenAnswer(ignored -> allocations.stream()
                .filter(allocation -> !Boolean.TRUE.equals(allocation.getPayment().getReversed())).toList());
        when(allocationRepository.findHistoryByInvoiceId(41L)).thenAnswer(ignored -> List.copyOf(allocations));
        when(allocationRepository.findByPaymentIdAndInvoiceId(anyLong(), anyLong())).thenAnswer(invocation -> allocations.stream()
                .filter(allocation -> allocation.getPayment().getId().equals(invocation.getArgument(0))
                        && allocation.getInvoice().getId().equals(invocation.getArgument(1))).findFirst());
        doAnswer(invocation -> { allocations.remove(invocation.getArgument(0)); return null; })
                .when(allocationRepository).delete(any(InvoicePaymentAllocation.class));

        when(tdsRepository.save(any(InvoiceTdsDeduction.class))).thenAnswer(invocation -> {
            InvoiceTdsDeduction deduction = invocation.getArgument(0);
            deductions.add(deduction);
            return deduction;
        });
        when(tdsRepository.findByPaymentId(anyLong())).thenAnswer(invocation -> deductions.stream()
                .filter(deduction -> deduction.getPayment().getId().equals(invocation.getArgument(0))).findFirst());
        doAnswer(invocation -> { deductions.remove(invocation.getArgument(0)); return null; })
                .when(tdsRepository).delete(any(InvoiceTdsDeduction.class));
        when(receiptRepository.save(any(PaymentReceipt.class))).thenAnswer(invocation -> {
            PaymentReceipt receipt = invocation.getArgument(0);
            receipts.add(receipt);
            return receipt;
        });
        when(receiptRepository.findByPaymentId(anyLong())).thenAnswer(invocation -> receipts.stream()
                .filter(receipt -> receipt.getPayment().getId().equals(invocation.getArgument(0))).findFirst());
        doAnswer(invocation -> { receipts.remove(invocation.getArgument(0)); return null; })
                .when(receiptRepository).delete(any(PaymentReceipt.class));

        when(creditAllocationRepository.findByInvoiceIdAndSourceTypeAndSourceReference(anyLong(), anyString(), anyString()))
                .thenAnswer(invocation -> creditAllocations.stream()
                        .filter(credit -> credit.getInvoice().getId().equals(invocation.getArgument(0))
                                && credit.getSourceType().equals(invocation.getArgument(1))
                                && credit.getSourceReference().equals(invocation.getArgument(2)))
                        .findFirst());
        when(creditAllocationRepository.save(any(InvoiceCreditAllocation.class))).thenAnswer(invocation -> {
            InvoiceCreditAllocation credit = invocation.getArgument(0);
            if (!creditAllocations.contains(credit)) creditAllocations.add(credit);
            return credit;
        });
        when(creditAllocationRepository.findAllByInvoiceIdAndReversedFalse(41L)).thenAnswer(ignored -> creditAllocations.stream()
                .filter(credit -> !Boolean.TRUE.equals(credit.getReversed())).toList());
        doAnswer(invocation -> { creditAllocations.remove(invocation.getArgument(0)); return null; })
                .when(creditAllocationRepository).delete(any(InvoiceCreditAllocation.class));
        when(creditNoteLinkRepository.findAllByInvoiceId(41L)).thenAnswer(ignored -> List.copyOf(creditNoteLinks));
        when(creditNoteLinkRepository.findAllByCreditNoteId(anyLong())).thenAnswer(invocation -> creditNoteLinks.stream()
                .filter(link -> link.getCreditNote().getId().equals(invocation.getArgument(0))).toList());
        when(creditNoteLinkRepository.findByInvoiceIdAndCreditNoteId(anyLong(), anyLong())).thenAnswer(invocation -> creditNoteLinks.stream()
                .filter(link -> link.getInvoice().getId().equals(invocation.getArgument(0))
                        && link.getCreditNote().getId().equals(invocation.getArgument(1))).findFirst());
        when(creditNoteLinkRepository.save(any(InvoiceCreditNoteLink.class))).thenAnswer(invocation -> {
            InvoiceCreditNoteLink link = invocation.getArgument(0);
            if (!creditNoteLinks.contains(link)) creditNoteLinks.add(link);
            return link;
        });
        doAnswer(invocation -> { creditNoteLinks.remove(invocation.getArgument(0)); return null; })
                .when(creditNoteLinkRepository).delete(any(InvoiceCreditNoteLink.class));
        when(reminderRepository.findAllByInvoiceIdOrderByCreatedAtDesc(41L)).thenReturn(List.of());
        when(communicationRepository.findAllByInvoiceIdOrderByCreatedAtDesc(41L)).thenReturn(List.of());
    }

    @Test
    void fullPaymentWithoutTdsMarksInvoicePaid() {
        var response = service.recordPayment(41L, payment("100000.00", "0.00", false, null, null, "full-no-tds"));

        assertThat(response.status()).isEqualTo("Paid");
        assertThat(response.cashAmountPaid()).isEqualByComparingTo("100000.00");
        assertThat(response.tdsSettled()).isEqualByComparingTo("0.00");
        assertThat(response.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(lifecycle.getPaidAt()).isNotNull();
        assertThat(receipts).singleElement().extracting(PaymentReceipt::getRemainingBalance).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void tdsCompletesSettlementUsingCashPlusDeduction() {
        lifecycle.getInvoice().setAmount(new BigDecimal("159300.00"));
        lifecycle.getInvoice().setBalanceAmount(new BigDecimal("159300.00"));
        lifecycle.getInvoice().setNotes("{\"Currency\":\"INR - Indian Rupee\",\"totals\":{\"taxableAmount\":\"135000.00\"}}");
        lifecycle.setBalanceDue(new BigDecimal("159300.00"));

        var response = service.recordPayment(41L, payment("145800.00", "0.00", true, "10.00", null, "full-with-tds"));

        assertThat(response.status()).isEqualTo("Paid");
        assertThat(response.cashAmountPaid()).isEqualByComparingTo("145800.00");
        assertThat(response.tdsSettled()).isEqualByComparingTo("13500.00");
        assertThat(response.totalSettled()).isEqualByComparingTo("159300.00");
        assertThat(response.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(deductions).singleElement().satisfies(deduction -> {
            assertThat(deduction.getBaseType()).isEqualTo("TAXABLE_VALUE");
            assertThat(deduction.getBaseAmount()).isEqualByComparingTo("135000.00");
            assertThat(deduction.getAmount()).isEqualByComparingTo("13500.00");
        });
    }

    @Test
    void bankChargesReduceNetCreditButNotInvoiceSettlement() {
        var response = service.recordPayment(41L, payment("100000.00", "500.00", false, null, null, "with-bank-charge"));

        assertThat(response.status()).isEqualTo("Paid");
        assertThat(response.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(payments).singleElement().satisfies(payment -> {
            assertThat(payment.getGrossAmountReceived()).isEqualByComparingTo("100000.00");
            assertThat(payment.getBankCharges()).isEqualByComparingTo("500.00");
            assertThat(payment.getNetBankCredit()).isEqualByComparingTo("99500.00");
        });
    }

    @Test
    void partialPaymentWithTdsKeepsOnlyTheUnsettledBalanceOpen() {
        lifecycle.getInvoice().setAmount(new BigDecimal("159300.00"));
        lifecycle.getInvoice().setBalanceAmount(new BigDecimal("159300.00"));
        lifecycle.getInvoice().setNotes("{\"totals\":{\"taxableAmount\":\"135000.00\"}}");
        lifecycle.setBalanceDue(new BigDecimal("159300.00"));

        var response = service.recordPayment(41L, payment("50000.00", "0.00", true, "10.00", null, "partial-with-tds"));

        assertThat(response.status()).isEqualTo("Partially Paid");
        assertThat(response.cashAmountPaid()).isEqualByComparingTo("50000.00");
        assertThat(response.tdsSettled()).isEqualByComparingTo("13500.00");
        assertThat(response.totalSettled()).isEqualByComparingTo("63500.00");
        assertThat(response.balanceDue()).isEqualByComparingTo("95800.00");
    }

    @Test
    void multiplePaymentsAreRetainedAndCompleteTheInvoice() {
        var partial = service.recordPayment(41L, payment("25000.00", "0.00", false, null, null, "payment-one"));
        var paid = service.recordPayment(41L, payment("75000.00", "0.00", false, null, null, "payment-two"));

        assertThat(partial.status()).isEqualTo("Partially Paid");
        assertThat(paid.status()).isEqualTo("Paid");
        assertThat(paid.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(paid.payments()).hasSize(2).allSatisfy(payment -> assertThat(payment.status()).isEqualTo("Paid"));
    }

    @Test
    void duplicateIdempotencyKeyDoesNotCreateSecondPayment() {
        RecordInvoicePaymentRequest request = payment("25000.00", "0.00", false, null, null, "same-request");

        service.recordPayment(41L, request);
        service.recordPayment(41L, request);

        assertThat(payments).hasSize(1);
        verify(paymentRepository, times(1)).saveAndFlush(any(InvoicePayment.class));
    }

    @Test
    void confirmedCustomerCreditIsStoredSeparatelyAndSettlesInvoice() {
        var response = service.recordPayment(41L, paymentWithCredit("75000.00", "25000.00", "with-credit"));

        assertThat(response.status()).isEqualTo("Paid");
        assertThat(response.cashAmountPaid()).isEqualByComparingTo("75000.00");
        assertThat(response.creditApplied()).isEqualByComparingTo("25000.00");
        assertThat(response.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(creditAllocations).singleElement().satisfies(credit -> {
            assertThat(credit.getSourceType()).isEqualTo("PAYMENT");
            assertThat(credit.getAmount()).isEqualByComparingTo("25000.00");
            assertThat(credit.getReversed()).isFalse();
        });
    }

    @Test
    void reversingPartialPaymentRestoresBalanceAndSentStatus() {
        var partial = service.recordPayment(41L, payment("25000.00", "0.00", false, null, null, "partial"));
        assertThat(partial.status()).isEqualTo("Partially Paid");
        assertThat(partial.balanceDue()).isEqualByComparingTo("75000.00");

        var reversed = service.reversePayment(41L, payments.get(0).getId(), new ReverseInvoicePaymentRequest("Incorrect bank entry"));

        assertThat(reversed.status()).isEqualTo("Sent");
        assertThat(reversed.cashAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(reversed.balanceDue()).isEqualByComparingTo("100000.00");
        assertThat(payments.get(0).getReversalReason()).isEqualTo("Incorrect bank entry");
    }

    @Test
    void deletingPaymentRemovesHistoryAndRestoresInvoiceBalance() {
        service.recordPayment(41L, payment("25000.00", "0.00", false, null, null, "delete-payment"));

        var response = service.deletePayment(41L, payments.get(0).getId());

        assertThat(response.status()).isEqualTo("Sent");
        assertThat(response.cashAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(response.balanceDue()).isEqualByComparingTo("100000.00");
        assertThat(response.payments()).isEmpty();
        assertThat(payments).isEmpty();
        assertThat(allocations).isEmpty();
        assertThat(receipts).isEmpty();
    }

    @Test
    void linkedCreditNoteIsCappedAtRemainingInvoiceBalance() {
        service.recordPayment(41L, payment("80000.00", "0.00", false, null, null, "partial-before-credit"));
        BusinessRecord creditNote = new BusinessRecord();
        creditNote.setId(81L);
        creditNote.setRecordNumber("CN-000081");
        InvoiceCreditNoteLink link = new InvoiceCreditNoteLink();
        link.setId(91L);
        link.setInvoice(lifecycle.getInvoice());
        link.setCreditNote(creditNote);
        link.setAmountApplied(new BigDecimal("30000.00"));
        creditNoteLinks.add(link);

        var response = service.findByInvoiceId(41L);

        assertThat(response.status()).isEqualTo("Paid");
        assertThat(response.creditNoteApplied()).isEqualByComparingTo("20000.00");
        assertThat(response.totalSettled()).isEqualByComparingTo("100000.00");
        assertThat(response.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(response.creditNotes()).singleElement().extracting(linked -> linked.amountApplied())
                .isEqualTo(new BigDecimal("30000.00"));
    }

    @Test
    void appliedCreditNoteClosesCreditAndClearsInvoiceBalanceUntilRemoved() {
        BusinessRecord creditNote = new BusinessRecord();
        creditNote.setId(81L);
        creditNote.setModule("sales");
        creditNote.setType("creditNotes");
        creditNote.setRecordNumber("CN-000081");
        creditNote.setPartyName("ABC Corporation");
        creditNote.setAmount(new BigDecimal("100000.00"));
        creditNote.setBalanceAmount(new BigDecimal("100000.00"));
        creditNote.setStatus("Unused");
        creditNote.setNotes("{\"Source Invoice ID\":\"41\"}");
        when(businessRecordRepository.findByModuleAndTypeAndId("sales", "creditNotes", 81L)).thenReturn(Optional.of(creditNote));

        var applied = service.applyCreditNote(41L, 81L, new com.intelliatech.app.dto.request.ApplyInvoiceCreditNoteRequest(new BigDecimal("100000.00")));

        assertThat(applied.status()).isEqualTo("Paid");
        assertThat(applied.creditNoteApplied()).isEqualByComparingTo("100000.00");
        assertThat(applied.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(creditNote.getStatus()).isEqualTo("Closed");
        assertThat(creditNote.getBalanceAmount()).isEqualByComparingTo("0.00");

        var removed = service.removeCreditNote(41L, 81L);

        assertThat(removed.status()).isEqualTo("Sent");
        assertThat(removed.creditNoteApplied()).isEqualByComparingTo("0.00");
        assertThat(removed.balanceDue()).isEqualByComparingTo("100000.00");
        assertThat(creditNote.getStatus()).isEqualTo("Unused");
        assertThat(creditNote.getBalanceAmount()).isEqualByComparingTo("100000.00");
    }

    @Test
    void eligibleCreditNoteInvoicesAreCustomerScopedAndKeepCurrentEditInvoice() {
        BusinessRecord customer = new BusinessRecord();
        customer.setId(7L);
        customer.setModule("sales");
        customer.setType("customers");
        customer.setPartyName("ABC Corporation");
        customer.setNotes("{\"billingAddress\":\"12 Main Street\",\"country\":\"India\",\"state\":\"Tamil Nadu (33)\"}");

        BusinessRecord creditNote = new BusinessRecord();
        creditNote.setId(81L);
        creditNote.setRecordNumber("CN-000081");
        InvoiceCreditNoteLink link = new InvoiceCreditNoteLink();
        link.setId(91L);
        link.setInvoice(lifecycle.getInvoice());
        link.setCreditNote(creditNote);
        link.setAmountApplied(new BigDecimal("100000.00"));
        creditNoteLinks.add(link);

        when(businessRecordRepository.findByModuleAndTypeAndId("sales", "customers", 7L)).thenReturn(Optional.of(customer));
        when(businessRecordRepository.findAllByModuleAndTypeAndPartyNameIgnoreCaseOrderByRecordDateDesc(
                "sales", "invoices", "ABC Corporation")).thenReturn(List.of(lifecycle.getInvoice()));
        when(lifecycleRepository.findByInvoiceId(41L)).thenReturn(Optional.of(lifecycle));

        assertThat(service.findEligibleForCreditNote(7L, null)).isEmpty();
        assertThat(service.findEligibleForCreditNote(7L, 81L)).singleElement().satisfies(invoice -> {
            assertThat(invoice.id()).isEqualTo(41L);
            assertThat(invoice.customerId()).isEqualTo(7L);
            assertThat(invoice.remainingEligibleAmount()).isEqualByComparingTo("100000.00");
            assertThat(invoice.billingAddress()).isEqualTo("12 Main Street");
        });
    }

    @Test
    void voidInvoiceClearsBalanceAndLocksActions() {
        var response = service.voidInvoice(41L, new VoidInvoiceRequest("Customer cancelled"));

        assertThat(response.status()).isEqualTo("Void");
        assertThat(response.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(response.voidReason()).isEqualTo("Customer cancelled");
        assertThat(response.actions().edit()).isFalse();
        assertThat(response.actions().recordPayment()).isFalse();
    }

    @Test
    void openSentInvoiceBecomesOverdueAfterDueDate() {
        lifecycle.getInvoice().setDueDate(LocalDate.now().minusDays(3));

        var response = service.findByInvoiceId(41L);

        assertThat(response.status()).isEqualTo("Overdue");
        assertThat(response.overdueDays()).isEqualTo(3);
        assertThat(response.balanceDue()).isEqualByComparingTo("100000.00");
    }

    private RecordInvoicePaymentRequest payment(
            String amount,
            String bankCharges,
            boolean tdsDeducted,
            String tdsPercentage,
            String tdsAmount,
            String idempotencyKey
    ) {
        return new RecordInvoicePaymentRequest(
                new BigDecimal(amount), LocalDate.now(), "Bank Transfer", "HDFC Bank", "REF-001",
                tdsDeducted, decimal(tdsPercentage), decimal(tdsAmount), null, "194C", null, null, null,
                new BigDecimal(bankCharges), BigDecimal.ZERO, "Payment test", null, false, idempotencyKey
        );
    }

    private RecordInvoicePaymentRequest paymentWithCredit(String amount, String credit, String idempotencyKey) {
        return new RecordInvoicePaymentRequest(
                new BigDecimal(amount), LocalDate.now(), "Bank Transfer", "HDFC Bank", "REF-CREDIT",
                false, null, null, null, null, null, null, null,
                BigDecimal.ZERO, new BigDecimal(credit), "Payment with confirmed customer credit", null, false, idempotencyKey
        );
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
