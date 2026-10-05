package com.intelliatech.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.intelliatech.app.entity.DocumentNumberPreference;
import com.intelliatech.app.dto.request.DocumentNumberPreferenceRequest;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.DocumentNumberPreferenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentNumberPreferenceServiceImplTest {

    @Mock
    private DocumentNumberPreferenceRepository repository;

    @Mock
    private BusinessRecordRepository businessRecordRepository;

    @InjectMocks
    private DocumentNumberPreferenceServiceImpl service;

    @Test
    void allocatesNextNumberFromLatestMatchingInvoice() {
        DocumentNumberPreference preference = preference("INV-2026", "-", "", "{####}", 1L, 1L);
        when(repository.findByDocumentTypeForUpdate("invoices")).thenReturn(Optional.of(preference));
        when(businessRecordRepository.findRecordNumbersByModuleAndType("sales", "invoices"))
                .thenReturn(List.of("INV-2026-0047", "LEGACY-9999"));
        when(businessRecordRepository.existsByRecordNumber("INV-2026-0048")).thenReturn(false);
        when(repository.save(any(DocumentNumberPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String allocated = service.allocateForCreate("invoices");

        assertThat(allocated).isEqualTo("INV-2026-0048");
        ArgumentCaptor<DocumentNumberPreference> captor = ArgumentCaptor.forClass(DocumentNumberPreference.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNextNumber()).isEqualTo(49L);
    }

    @Test
    void preservesSlashSeparatedFinancialYearFormat() {
        DocumentNumberPreference preference = preference("INT/26-27/", "/", "", "{0000}", 1L, 1L);
        when(repository.findByDocumentTypeForUpdate("invoices")).thenReturn(Optional.of(preference));
        when(businessRecordRepository.findRecordNumbersByModuleAndType("sales", "invoices"))
                .thenReturn(List.of("INT/26-27/0125"));
        when(businessRecordRepository.existsByRecordNumber("INT/26-27/0126")).thenReturn(false);
        when(repository.save(any(DocumentNumberPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.allocateForCreate("invoices")).isEqualTo("INT/26-27/0126");
        assertThat(preference.getNextNumber()).isEqualTo(127L);
    }

    @Test
    void skipsAConflictingGlobalRecordNumber() {
        DocumentNumberPreference preference = preference("INV", "-", "", "000", 10L, 10L);
        when(repository.findByDocumentTypeForUpdate("invoices")).thenReturn(Optional.of(preference));
        when(businessRecordRepository.findRecordNumbersByModuleAndType("sales", "invoices"))
                .thenReturn(List.of());
        when(businessRecordRepository.existsByRecordNumber("INV-010")).thenReturn(true);
        when(businessRecordRepository.existsByRecordNumber("INV-011")).thenReturn(false);
        when(repository.save(any(DocumentNumberPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.allocateForCreate("invoices")).isEqualTo("INV-011");
        assertThat(preference.getNextNumber()).isEqualTo(12L);
    }

    @Test
    void allocatesNextCustomerCodeFromExistingCustomers() {
        DocumentNumberPreference preference = preference("CUST", "-", "", "0000", 1L, 1L);
        preference.setDocumentType("customers");
        when(repository.findByDocumentTypeForUpdate("customers")).thenReturn(Optional.of(preference));
        when(businessRecordRepository.findRecordNumbersByModuleAndType("sales", "customers"))
                .thenReturn(List.of("CUST-0001", "CUST-0004", "LEGACY-CUSTOMER"));
        when(businessRecordRepository.existsByRecordNumber("CUST-0005")).thenReturn(false);
        when(repository.save(any(DocumentNumberPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.allocateForCreate("customers")).isEqualTo("CUST-0005");
        assertThat(preference.getNextNumber()).isEqualTo(6L);
    }

    @Test
    void rejectsInvalidCustomerCodeConfiguration() {
        DocumentNumberPreferenceRequest request = new DocumentNumberPreferenceRequest(
                true, "BAD PREFIX!", "", "-", "00", 1L, 1L);

        assertThatThrownBy(() -> service.save("customers", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prefix");
    }

    private DocumentNumberPreference preference(
            String prefix,
            String separator,
            String suffix,
            String format,
            long startingNumber,
            long nextNumber
    ) {
        DocumentNumberPreference preference = new DocumentNumberPreference();
        preference.setDocumentType("invoices");
        preference.setAutoGenerate(true);
        preference.setPrefix(prefix);
        preference.setSeparator(separator);
        preference.setSuffix(suffix);
        preference.setNumberFormat(format);
        preference.setStartingNumber(startingNumber);
        preference.setNextNumber(nextNumber);
        return preference;
    }
}
