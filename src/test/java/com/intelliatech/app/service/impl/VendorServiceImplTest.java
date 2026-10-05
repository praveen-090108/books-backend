package com.intelliatech.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.VendorBankDetailsRequest;
import com.intelliatech.app.dto.request.VendorRequest;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.Vendor;
import com.intelliatech.app.entity.VendorStatus;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceConflictException;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.VendorRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class VendorServiceImplTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private BusinessRecordRepository businessRecordRepository;

    private VendorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VendorServiceImpl(vendorRepository, businessRecordRepository, new ObjectMapper());
    }

    @Test
    void optionalBankSectionDoesNotCreateBankDetails() {
        prepareCreate();

        var response = service.create(request(emptyBank()));

        assertThat(response.vendorNumber()).isEqualTo("VEN-0001");
        assertThat(response.bankDetails()).isNull();
    }

    @Test
    void bankAccountIsStoredButOnlyMaskedValueIsReturned() {
        prepareCreate();
        var bank = new VendorBankDetailsRequest(
                "Accounts Team", "ABC Supplies", "HDFC Bank", "123456789012", "123456789012",
                "HDFC0001234", "Indore", "Current", "HDFCINBB", "", "India",
                "MG Road", "abc@upi", "Primary account"
        );

        var response = service.create(request(bank));

        assertThat(response.bankDetails().maskedAccountNumber()).isEqualTo("XXXX XXXX 9012");
        assertThat(response.bankDetails().toString()).doesNotContain("123456789012");
    }

    @Test
    void updateRejectsDuplicateVendorNameWithinOrganization() {
        Vendor vendor = vendor(7L, "VEN-0007", "Existing Vendor");
        when(vendorRepository.findByIdAndOrganizationId(7L, 1L)).thenReturn(Optional.of(vendor));
        when(vendorRepository.existsByOrganizationIdAndVendorNameIgnoreCaseAndIdNot(1L, "ABC Supplies", 7L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, request(emptyBank())))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deleteIsBlockedWhenVendorHasTransactions() {
        Vendor vendor = vendor(4L, "VEN-0004", "ABC Supplies");
        when(vendorRepository.findByIdAndOrganizationId(4L, 1L)).thenReturn(Optional.of(vendor));
        when(businessRecordRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(transaction(9L, "bills", "BILL-009", "250.00", "100.00", LocalDate.now())));

        assertThatThrownBy(() -> service.delete(4L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("transactions");
    }

    @Test
    void statementUsesAuthoritativeOpeningAndRunningBalances() {
        Vendor vendor = vendor(3L, "VEN-0003", "ABC Supplies");
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        List<BusinessRecord> records = List.of(
                transaction(1L, "bills", "BILL-001", "100.00", "100.00", from.minusDays(2)),
                transaction(2L, "bills", "BILL-002", "250.00", "150.00", from.plusDays(2)),
                transaction(3L, "paymentsMade", "PAY-003", "100.00", "0.00", from.plusDays(3)),
                transaction(4L, "vendorCredits", "VC-004", "25.00", "25.00", from.plusDays(4))
        );
        when(vendorRepository.findByIdAndOrganizationId(3L, 1L)).thenReturn(Optional.of(vendor));
        when(businessRecordRepository.findAll(any(Specification.class))).thenReturn(records);

        var statement = service.statement(3L, from, to, "all");

        assertThat(statement.summary().openingBalance()).isEqualByComparingTo("100.00");
        assertThat(statement.summary().totalDebit()).isEqualByComparingTo("250.00");
        assertThat(statement.summary().totalCredit()).isEqualByComparingTo("125.00");
        assertThat(statement.summary().closingBalance()).isEqualByComparingTo("225.00");
        assertThat(statement.transactions()).extracting(line -> line.balance().toPlainString())
                .containsExactly("350.00", "250.00", "225.00");
    }

    @Test
    void statementPdfUsesPortraitAccountStatementLayout() throws Exception {
        Vendor vendor = vendor(3L, "VEN-0003", "ABC Supplies");
        vendor.setBillingAddressLine1("12 Industrial Area");
        vendor.setBillingCity("Indore");
        vendor.setBillingState("Madhya Pradesh");
        vendor.setBillingPincode("452001");
        vendor.setBillingCountry("India");
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(vendorRepository.findByIdAndOrganizationId(3L, 1L)).thenReturn(Optional.of(vendor));
        when(businessRecordRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(transaction(2L, "bills", "BILL-002", "250.00", "150.00", from.plusDays(2))));

        byte[] pdf = service.statementPdf(3L, from, to, "all");
        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+", " ");

        assertThat(reader.getPageSize(1).getHeight()).isGreaterThan(reader.getPageSize(1).getWidth());
        assertThat(text).contains(
                "STATEMENT OF ACCOUNTS",
                "Account Summary",
                "Opening Balance",
                "Billed Amount",
                "Amount Paid",
                "Transactions",
                "Payments",
                "Balance Due"
        );
    }

    private void prepareCreate() {
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> {
            Vendor saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(businessRecordRepository.findByRecordNumber(any())).thenReturn(Optional.empty());
    }

    private VendorRequest request(VendorBankDetailsRequest bank) {
        return new VendorRequest(
                "ABC Supplies", "ABC Supplies", "ABC Supplies Pvt. Ltd.", "Supplier", "Madhya Pradesh",
                "INR - Indian Rupee", "Net 30", "Business", "", "", VendorStatus.ACTIVE,
                "Amit Shah", "accounts@abc.example", "+91 98765 43210", "", "https://abc.example",
                "12 Industrial Area", "Phase 1", "Indore", "Madhya Pradesh", "452001", "India",
                "", "", "", "", "", "India", bank
        );
    }

    private VendorBankDetailsRequest emptyBank() {
        return new VendorBankDetailsRequest("", "", "", "", "", "", "", "", "", "", "India", "", "", "");
    }

    private Vendor vendor(Long id, String number, String name) {
        Vendor vendor = new Vendor();
        vendor.setId(id);
        vendor.setOrganizationId(1L);
        vendor.setVendorNumber(number);
        vendor.setVendorName(name);
        vendor.setDisplayName(name);
        vendor.setCurrency("INR - Indian Rupee");
        vendor.setStatus(VendorStatus.ACTIVE);
        return vendor;
    }

    private BusinessRecord transaction(
            Long id,
            String type,
            String number,
            String amount,
            String balance,
            LocalDate date
    ) {
        BusinessRecord record = new BusinessRecord();
        record.setId(id);
        record.setModule("purchases");
        record.setType(type);
        record.setRecordNumber(number);
        record.setPartyName("ABC Supplies");
        record.setStatus("Paid");
        record.setAmount(new BigDecimal(amount));
        record.setBalanceAmount(new BigDecimal(balance));
        record.setRecordDate(date);
        return record;
    }
}
