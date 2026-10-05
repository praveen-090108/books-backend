package com.intelliatech.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.BusinessRecordRequest;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.mapper.BusinessRecordMapper;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.service.DocumentNumberPreferenceService;
import com.intelliatech.app.service.InvoiceLifecycleService;
import com.intelliatech.app.security.CurrentUserService;
import com.intelliatech.app.security.DataScopeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BusinessRecordServiceImplTest {

    @Mock
    private BusinessRecordRepository repository;

    @Mock
    private DocumentNumberPreferenceService documentNumberPreferenceService;

    @Mock
    private InvoiceLifecycleService invoiceLifecycleService;
    @Mock private CurrentUserService currentUserService;
    @Mock private DataScopeService dataScopeService;
    @Mock private JdbcTemplate jdbcTemplate;

    private BusinessRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BusinessRecordServiceImpl(
                repository,
                new BusinessRecordMapper(),
                documentNumberPreferenceService,
                invoiceLifecycleService,
                new ObjectMapper(),
                currentUserService,
                dataScopeService,
                jdbcTemplate
        );
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void invoiceCreateAlwaysUsesBackendAllocatedNumber() {
        when(documentNumberPreferenceService.allocateForCreate("invoices")).thenReturn("INV-2026-0048");
        when(repository.existsByRecordNumber("INV-2026-0048")).thenReturn(false);
        when(repository.save(any(BusinessRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create("sales", "invoices", request("SOURCE-NUMBER"));

        assertThat(response.recordNumber()).isEqualTo("INV-2026-0048");
        assertThat(response.notes()).contains("\"Invoice#\":\"INV-2026-0048\"");
        assertThat(response.notes()).contains("\"InvoiceNumber\":\"INV-2026-0048\"");
    }

    @Test
    void invoiceEditKeepsItsExistingNumber() {
        BusinessRecord existing = new BusinessRecord();
        existing.setId(42L);
        existing.setModule("sales");
        existing.setType("invoices");
        existing.setRecordNumber("INV-2026-0048");
        existing.setPartyName("Existing Customer");
        existing.setStatus("Draft");
        existing.setAmount(BigDecimal.TEN);
        existing.setBalanceAmount(BigDecimal.TEN);
        existing.setRecordDate(LocalDate.of(2026, 7, 14));
        when(repository.findByModuleAndTypeAndId("sales", "invoices", 42L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        var response = service.update("sales", "invoices", 42L, request("ATTEMPTED-CHANGE"));

        assertThat(response.recordNumber()).isEqualTo("INV-2026-0048");
        verify(documentNumberPreferenceService, never()).allocateForCreate(any());
    }

    @Test
    void customerCreateAlwaysUsesBackendAllocatedCode() {
        when(documentNumberPreferenceService.allocateForCreate("customers")).thenReturn("CUST-0008");
        when(repository.existsByRecordNumber("CUST-0008")).thenReturn(false);
        when(repository.save(any(BusinessRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create("sales", "customers", customerRequest("CLIENT-SUPPLIED"));

        assertThat(response.recordNumber()).isEqualTo("CUST-0008");
        verify(documentNumberPreferenceService).allocateForCreate("customers");
    }

    @Test
    void customerEditKeepsItsExistingCode() {
        BusinessRecord existing = new BusinessRecord();
        existing.setId(84L);
        existing.setModule("sales");
        existing.setType("customers");
        existing.setRecordNumber("LEGACY-0042");
        existing.setPartyName("Existing Customer");
        existing.setStatus("Active");
        existing.setAmount(BigDecimal.ZERO);
        existing.setBalanceAmount(BigDecimal.ZERO);
        existing.setRecordDate(LocalDate.of(2026, 7, 14));
        when(repository.findByModuleAndTypeAndId("sales", "customers", 84L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        var response = service.update("sales", "customers", 84L, customerRequest("ATTEMPTED-CHANGE"));

        assertThat(response.recordNumber()).isEqualTo("LEGACY-0042");
        verify(documentNumberPreferenceService, never()).allocateForCreate(any());
    }

    private BusinessRecordRequest request(String recordNumber) {
        return new BusinessRecordRequest(
                recordNumber,
                "ABC Corporation",
                "accounts@example.com",
                "+91 99999 99999",
                "Madhya Pradesh (23)",
                "Fixed Cost",
                "Draft",
                "Unpaid",
                BigDecimal.valueOf(1180),
                BigDecimal.valueOf(1180),
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 29),
                null,
                "PO-001",
                "Net 15",
                "Admin",
                "{\"Invoice#\":\"SOURCE-NUMBER\",\"InvoiceNumber\":\"SOURCE-NUMBER\"}",
                null,
                null,
                null
        );
    }

    private BusinessRecordRequest customerRequest(String recordNumber) {
        return new BusinessRecordRequest(
                recordNumber, "ABC Corporation", "accounts@example.com", "+91 99999 99999", "Indore",
                "Corporate Customers", "Active", "Business", BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2026, 7, 14), null, null, "", "Net 30", "Admin",
                "{\"country\":\"India\",\"state\":\"Madhya Pradesh\",\"shippingCountry\":\"India\","
                        + "\"shippingState\":\"Madhya Pradesh\",\"gstTreatment\":\"Unregistered Business\","
                        + "\"gstCategory\":\"Unregistered\",\"attachments\":[]}",
                null, null, null
        );
    }
}
