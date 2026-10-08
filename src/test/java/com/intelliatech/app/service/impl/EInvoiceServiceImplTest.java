package com.intelliatech.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intelliatech.app.dto.request.CancelIrnRequest;
import com.intelliatech.app.dto.response.IrpGenerateResult;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.EInvoiceDetail;
import com.intelliatech.app.entity.EInvoiceStatus;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.EInvoiceApiLogRepository;
import com.intelliatech.app.repository.EInvoiceDetailRepository;
import com.intelliatech.app.service.InvoiceToEInvoiceMapper;
import com.intelliatech.app.service.IrpClient;
import com.intelliatech.app.service.IrpConfigurationService;
import com.intelliatech.app.entity.IrpConfiguration;
import com.intelliatech.app.entity.IrpEnvironment;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EInvoiceServiceImplTest {

    @Mock BusinessRecordRepository records;
    @Mock EInvoiceDetailRepository details;
    @Mock EInvoiceApiLogRepository logs;
    @Mock InvoiceToEInvoiceMapper mapper;
    @Mock IrpClient irpClient;
    @Mock IrpConfigurationService configurations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EInvoiceServiceImpl service;
    private BusinessRecord creditNote;

    @BeforeEach
    void setUp() {
        var config=new IrpConfiguration(); config.setProvider("EY_IRP_5"); config.setEnvironment(IrpEnvironment.SANDBOX); config.setGstin("23AAECI4774Q1Z0");
        lenient().when(configurations.getActiveConfiguration(1L)).thenReturn(config);
        service = new EInvoiceServiceImpl(records, details, logs, mapper, irpClient, configurations, objectMapper);
        creditNote = new BusinessRecord();
        creditNote.setId(81L);
        creditNote.setModule("sales");
        creditNote.setType("creditNotes");
        creditNote.setRecordNumber("CN-2026-001");
        when(records.findCreditNoteForUpdate(81L)).thenReturn(Optional.of(creditNote));
        lenient().when(details.save(any(EInvoiceDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(details.saveAndFlush(any(EInvoiceDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generatesCreditNoteIrnWithoutChangingOriginalInvoice() {
        ObjectNode request = objectMapper.createObjectNode();
        request.putObject("DocDtls").put("Typ", "CRN");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("Irn", "credit-note-irn");
        response.put("AckNo", "123456");
        response.put("AckDt", "2026-10-08 10:30:00");
        response.put("SignedInvoice", "signed-credit-note");
        response.put("SignedQRCode", "signed-credit-note-qr");
        when(mapper.map(creditNote)).thenReturn(request);
        when(irpClient.generateIrn(request)).thenReturn(new IrpGenerateResult(true, 200, response, response, null, null));

        var generated = service.generateCreditNote(81L);

        assertThat(generated.creditNoteId()).isEqualTo(81L);
        assertThat(generated.invoiceId()).isNull();
        assertThat(generated.status()).isEqualTo(EInvoiceStatus.GENERATED);
        assertThat(generated.irn()).isEqualTo("credit-note-irn");
        assertThat(generated.qrCodeDataUrl()).startsWith("data:image/png;base64,");
        verify(records, never()).findInvoiceForUpdate(any());
    }

    @Test
    void preventsDuplicateCreditNoteIrnSubmission() {
        EInvoiceDetail existing = new EInvoiceDetail();
        existing.setCreditNote(creditNote);
        existing.setStatus(EInvoiceStatus.GENERATED);
        existing.setIrn("existing-irn");
        when(details.findByCreditNoteIdForUpdate(81L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.generateCreditNote(81L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been generated");
        verify(irpClient, never()).generateIrn(any());
    }

    @Test
    void cancelsOnlyTheCreditNoteIrnAndPreservesItsAuditIdentity() {
        EInvoiceDetail existing = new EInvoiceDetail();
        existing.setCreditNote(creditNote);
        existing.setStatus(EInvoiceStatus.GENERATED);
        existing.setIrn("credit-note-irn");
        existing.setGeneratedAt(LocalDateTime.now());
        when(details.findByCreditNoteIdForUpdate(81L)).thenReturn(Optional.of(existing));
        ObjectNode cancelled = objectMapper.createObjectNode();
        cancelled.put("Irn", "credit-note-irn");
        cancelled.put("CancelDate", "2026-10-08 11:00:00");
        when(irpClient.cancelIrn("credit-note-irn", "1", "Incorrect data"))
                .thenReturn(new IrpGenerateResult(true, 200, cancelled, cancelled, null, null));

        var result = service.cancelCreditNote(81L, new CancelIrnRequest("1", "Incorrect data"));

        assertThat(result.creditNoteId()).isEqualTo(81L);
        assertThat(result.invoiceId()).isNull();
        assertThat(result.status()).isEqualTo(EInvoiceStatus.CANCELLED);
        verify(records, never()).findInvoiceForUpdate(any());
    }
}
