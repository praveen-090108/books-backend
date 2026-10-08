package com.intelliatech.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.response.EInvoiceResponse;
import com.intelliatech.app.dto.request.CancelIrnRequest;
import com.intelliatech.app.dto.response.IrpGenerateResult;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.EInvoiceApiLog;
import com.intelliatech.app.entity.EInvoiceApiType;
import com.intelliatech.app.entity.EInvoiceDetail;
import com.intelliatech.app.entity.EInvoiceStatus;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.EInvoiceApiLogRepository;
import com.intelliatech.app.repository.EInvoiceDetailRepository;
import com.intelliatech.app.service.EInvoiceService;
import com.intelliatech.app.service.InvoiceToEInvoiceMapper;
import com.intelliatech.app.service.IrpClient;
import com.intelliatech.app.service.IrpConfigurationService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EInvoiceServiceImpl implements EInvoiceService {

    private static final DateTimeFormatter ACK_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BusinessRecordRepository records;
    private final EInvoiceDetailRepository details;
    private final EInvoiceApiLogRepository logs;
    private final InvoiceToEInvoiceMapper mapper;
    private final IrpClient irpClient;
    private final IrpConfigurationService irpConfigurations;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public EInvoiceResponse findByInvoiceId(Long invoiceId) {
        BusinessRecord invoice = invoice(invoiceId);
        return details.findByInvoiceId(invoiceId).map(this::response).orElseGet(() -> emptyResponse(invoice));
    }

    @Override
    @Transactional(readOnly = true)
    public EInvoiceResponse findByCreditNoteId(Long creditNoteId) {
        BusinessRecord creditNote = creditNote(creditNoteId);
        return details.findByCreditNoteId(creditNoteId).map(this::response).orElseGet(() -> emptyResponse(creditNote));
    }

    @Override
    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public EInvoiceResponse generate(Long invoiceId) {
        BusinessRecord invoice = records.findInvoiceForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        EInvoiceDetail detail = details.findByInvoiceIdForUpdate(invoiceId).orElseGet(() -> newDetail(invoice));
        return generateDocument(invoice, detail, false);
    }

    @Override
    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public EInvoiceResponse generateCreditNote(Long creditNoteId) {
        BusinessRecord creditNote = records.findCreditNoteForUpdate(creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit Note not found"));
        EInvoiceDetail detail = details.findByCreditNoteIdForUpdate(creditNoteId)
                .orElseGet(() -> newCreditNoteDetail(creditNote));
        return generateDocument(creditNote, detail, true);
    }

    private EInvoiceResponse generateDocument(BusinessRecord document, EInvoiceDetail detail, boolean creditNote) {
        String label = creditNote ? "Credit Note" : "invoice";
        if (detail.getStatus() == EInvoiceStatus.GENERATED || detail.getStatus() == EInvoiceStatus.CANCELLED) {
            throw new IllegalStateException("IRN has already been generated for this " + label);
        }

        var activeConfiguration = irpConfigurations.getActiveConfiguration(1L);
        detail.setIrpProvider(activeConfiguration.getProvider());
        detail.setIrpEnvironment(activeConfiguration.getEnvironment());
        detail.setIrpGstin(activeConfiguration.getGstin());
        JsonNode request;
        try {
            request = mapper.map(document);
        } catch (IllegalArgumentException exception) {
            markFailure(detail, "VALIDATION_FAILED", exception.getMessage());
            details.save(detail);
            saveLog(document, creditNote, EInvoiceApiType.GENERATE_IRN, null, null, 400, false, "VALIDATION_FAILED", exception.getMessage());
            throw exception;
        }

        detail.setStatus(EInvoiceStatus.PROCESSING);
        detail.setIrpRequest(toJson(request));
        detail.setErrorCode(null);
        detail.setErrorMessage(null);
        details.saveAndFlush(detail);

        IrpGenerateResult result;
        try {
            result = irpClient.generateIrn(request);
        } catch (IllegalStateException exception) {
            markFailure(detail, "IRP_AUTH_OR_TRANSPORT_FAILED", exception.getMessage());
            details.save(detail);
            saveLog(document, creditNote, EInvoiceApiType.GENERATE_IRN, request, null, 0, false,
                    "IRP_AUTH_OR_TRANSPORT_FAILED", exception.getMessage());
            throw exception;
        }
        String rawResponse = toJson(result.rawResponse());
        saveLog(document, creditNote, EInvoiceApiType.GENERATE_IRN, request, result.rawResponse(), result.httpStatus(), result.success(),
                result.errorCode(), result.errorMessage());
        if (!result.success()) {
            markFailure(detail, result.errorCode(), result.errorMessage());
            detail.setIrpResponse(rawResponse);
            details.save(detail);
            throw new IllegalStateException("Generate IRN failed: " + result.errorMessage());
        }

        JsonNode data = result.decryptedData();
        String irn;
        String ackNo;
        String ackDate;
        try {
            irn = required(data, "Irn");
            ackNo = required(data, "AckNo");
            ackDate = required(data, "AckDt");
        } catch (IllegalStateException exception) {
            markFailure(detail, "INVALID_IRP_RESPONSE", exception.getMessage());
            detail.setIrpResponse(toJson(data));
            details.save(detail);
            throw exception;
        }
        detail.setIrn(irn);
        detail.setAcknowledgementNumber(ackNo);
        try {
            detail.setAcknowledgementDate(LocalDateTime.parse(ackDate, ACK_DATE));
        } catch (Exception exception) {
            markFailure(detail, "INVALID_IRP_RESPONSE", "IRP returned an invalid AckDt");
            details.save(detail);
            throw new IllegalStateException("Generate IRN failed: IRP returned an invalid AckDt");
        }
        detail.setSignedInvoice(text(data, "SignedInvoice"));
        detail.setSignedQrCode(text(data, "SignedQRCode"));
        detail.setStatus(EInvoiceStatus.GENERATED);
        detail.setGeneratedAt(LocalDateTime.now());
        detail.setIrpResponse(toJson(data));
        detail.setErrorCode(null);
        detail.setErrorMessage(null);
        return response(details.save(detail));
    }

    @Override
    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public EInvoiceResponse cancel(Long invoiceId, CancelIrnRequest request) {
        BusinessRecord invoice = records.findInvoiceForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        EInvoiceDetail detail = details.findByInvoiceIdForUpdate(invoiceId)
                .orElseThrow(() -> new IllegalStateException("IRN has not been generated for this invoice"));
        return cancelDocument(invoice, detail, request, false);
    }

    @Override
    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public EInvoiceResponse cancelCreditNote(Long creditNoteId, CancelIrnRequest request) {
        BusinessRecord creditNote = records.findCreditNoteForUpdate(creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit Note not found"));
        EInvoiceDetail detail = details.findByCreditNoteIdForUpdate(creditNoteId)
                .orElseThrow(() -> new IllegalStateException("IRN has not been generated for this Credit Note"));
        return cancelDocument(creditNote, detail, request, true);
    }

    private EInvoiceResponse cancelDocument(BusinessRecord document, EInvoiceDetail detail, CancelIrnRequest request,
                                             boolean creditNote) {
        if (detail.getStatus() == EInvoiceStatus.CANCELLED) return response(detail);
        if (detail.getStatus() != EInvoiceStatus.GENERATED || !StringUtils.hasText(detail.getIrn())) {
            throw new IllegalStateException("Only an active generated IRN can be cancelled");
        }
        LocalDateTime irpGeneratedAt = detail.getAcknowledgementDate() != null
                ? detail.getAcknowledgementDate() : detail.getGeneratedAt();
        if (irpGeneratedAt != null && Duration.between(irpGeneratedAt, LocalDateTime.now()).toHours() >= 24) {
            throw new IllegalStateException("IRP permits IRN cancellation only within 24 hours of generation");
        }
        String remarks = request.remarks() == null ? "" : request.remarks().trim();
        JsonNode requestPayload = objectMapper.valueToTree(java.util.Map.of(
                "Irn", detail.getIrn(), "CnlRsn", request.reasonCode(), "CnlRem", remarks));
        IrpGenerateResult result = detail.getIrpEnvironment() == null
                ? irpClient.cancelIrn(detail.getIrn(), request.reasonCode(), remarks)
                : irpClient.cancelIrn(detail.getIrn(), request.reasonCode(), remarks, detail.getIrpEnvironment());
        saveLog(document, creditNote, EInvoiceApiType.CANCEL_IRN, requestPayload, result.rawResponse(), result.httpStatus(),
                result.success(), result.errorCode(), result.errorMessage());
        if (!result.success()) {
            detail.setErrorCode(truncate(result.errorCode(), 64));
            detail.setErrorMessage(truncate(result.errorMessage(), 2000));
            details.save(detail);
            throw new IllegalStateException("Cancel IRN failed: " + result.errorMessage());
        }
        JsonNode data = result.decryptedData();
        if (!detail.getIrn().equals(text(data, "Irn"))) {
            throw new IllegalStateException("Cancel IRN failed: IRP response IRN does not match this "
                    + (creditNote ? "Credit Note" : "invoice"));
        }
        detail.setStatus(EInvoiceStatus.CANCELLED);
        detail.setCancelledAt(parseDateTime(text(data, "CancelDate"), LocalDateTime.now()));
        detail.setCancelReason(request.reasonCode());
        detail.setCancelRemarks(remarks);
        detail.setIrpResponse(toJson(data));
        detail.setErrorCode(null);
        detail.setErrorMessage(null);
        return response(details.save(detail));
    }

    @Override
    @Transactional(noRollbackFor = IllegalStateException.class)
    public EInvoiceResponse refresh(Long invoiceId) {
        BusinessRecord invoice = records.findInvoiceForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        EInvoiceDetail detail = details.findByInvoiceIdForUpdate(invoiceId)
                .orElseThrow(() -> new IllegalStateException("IRN has not been generated for this invoice"));
        return refreshDocument(invoice, detail, false);
    }

    @Override
    @Transactional(noRollbackFor = IllegalStateException.class)
    public EInvoiceResponse refreshCreditNote(Long creditNoteId) {
        BusinessRecord creditNote = records.findCreditNoteForUpdate(creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit Note not found"));
        EInvoiceDetail detail = details.findByCreditNoteIdForUpdate(creditNoteId)
                .orElseThrow(() -> new IllegalStateException("IRN has not been generated for this Credit Note"));
        return refreshDocument(creditNote, detail, true);
    }

    private EInvoiceResponse refreshDocument(BusinessRecord document, EInvoiceDetail detail, boolean creditNote) {
        if (!StringUtils.hasText(detail.getIrn())) throw new IllegalStateException("IRN is missing for this invoice");
        LocalDateTime irpGeneratedAt = detail.getAcknowledgementDate() != null
                ? detail.getAcknowledgementDate() : detail.getGeneratedAt();
        if (irpGeneratedAt != null && Duration.between(irpGeneratedAt, LocalDateTime.now()).toHours() >= 48) {
            throw new IllegalStateException("IRP permits Get IRN Details only within two days of IRN generation");
        }
        JsonNode requestPayload = objectMapper.valueToTree(java.util.Map.of("Irn", detail.getIrn()));
        IrpGenerateResult result = detail.getIrpEnvironment() == null
                ? irpClient.getIrn(detail.getIrn())
                : irpClient.getIrn(detail.getIrn(), detail.getIrpEnvironment());
        saveLog(document, creditNote, EInvoiceApiType.GET_IRN, requestPayload, result.rawResponse(), result.httpStatus(),
                result.success(), result.errorCode(), result.errorMessage());
        if (!result.success()) {
            detail.setErrorCode(truncate(result.errorCode(), 64));
            detail.setErrorMessage(truncate(result.errorMessage(), 2000));
            details.save(detail);
            throw new IllegalStateException("Get IRN Details failed: " + result.errorMessage());
        }
        JsonNode data = result.decryptedData();
        if (!detail.getIrn().equals(text(data, "Irn"))) {
            throw new IllegalStateException("Get IRN Details failed: IRP response IRN does not match this "
                    + (creditNote ? "Credit Note" : "invoice"));
        }
        applyIrpDetails(detail, data);
        return response(details.save(detail));
    }

    private BusinessRecord invoice(Long invoiceId) {
        return records.findByModuleAndTypeAndId("sales", "invoices", invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    private BusinessRecord creditNote(Long creditNoteId) {
        return records.findByModuleAndTypeAndId("sales", "creditNotes", creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit Note not found"));
    }

    private EInvoiceDetail newDetail(BusinessRecord invoice) {
        EInvoiceDetail detail = new EInvoiceDetail();
        detail.setInvoice(invoice);
        detail.setStatus(EInvoiceStatus.NOT_GENERATED);
        return detail;
    }

    private EInvoiceDetail newCreditNoteDetail(BusinessRecord creditNote) {
        EInvoiceDetail detail = new EInvoiceDetail();
        detail.setCreditNote(creditNote);
        detail.setStatus(EInvoiceStatus.NOT_GENERATED);
        return detail;
    }

    private void markFailure(EInvoiceDetail detail, String code, String message) {
        detail.setStatus(EInvoiceStatus.FAILED);
        detail.setErrorCode(truncate(code, 64));
        detail.setErrorMessage(truncate(message, 2000));
    }

    private void saveLog(BusinessRecord document, boolean creditNote, EInvoiceApiType apiType, JsonNode request, JsonNode response, int httpStatus,
                         boolean success, String code, String message) {
        EInvoiceApiLog log = new EInvoiceApiLog();
        if (creditNote) log.setCreditNote(document);
        else log.setInvoice(document);
        log.setApiType(apiType);
        log.setRequestPayload(toJson(request));
        log.setResponsePayload(toJson(response));
        log.setHttpStatus(httpStatus == 0 ? null : httpStatus);
        log.setSuccess(success);
        log.setErrorCode(truncate(code, 64));
        log.setErrorMessage(truncate(message, 2000));
        logs.save(log);
    }

    private EInvoiceResponse emptyResponse(BusinessRecord invoice) {
        boolean creditNote = "creditNotes".equals(invoice.getType());
        return new EInvoiceResponse(creditNote ? null : invoice.getId(), creditNote ? invoice.getId() : null,
                null, null, null,
                EInvoiceStatus.NOT_GENERATED, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    private EInvoiceResponse response(EInvoiceDetail detail) {
        return new EInvoiceResponse(detail.getInvoice() == null ? null : detail.getInvoice().getId(),
                detail.getCreditNote() == null ? null : detail.getCreditNote().getId(), detail.getIrpProvider(),
                detail.getIrpEnvironment(), detail.getIrpGstin(), detail.getStatus(), detail.getIrn(),
                detail.getAcknowledgementNumber(), detail.getAcknowledgementDate(), detail.getSignedQrCode(),
                qrCodeDataUrl(detail.getSignedQrCode()),
                detail.getGeneratedAt(), detail.getCancelledAt(), detail.getEwayBillNumber(), detail.getEwayBillDate(),
                detail.getEwayBillValidTill(), detail.getRemarks(), detail.getErrorCode(), detail.getErrorMessage());
    }

    private String qrCodeDataUrl(String signedQrCode) {
        if (!StringUtils.hasText(signedQrCode)) return null;
        try {
            var hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2
            );
            var matrix = new QRCodeWriter().encode(signedQrCode, BarcodeFormat.QR_CODE, 320, 320, hints);
            var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            var output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyIrpDetails(EInvoiceDetail detail, JsonNode data) {
        detail.setAcknowledgementNumber(text(data, "AckNo"));
        detail.setAcknowledgementDate(parseDateTime(text(data, "AckDt"), detail.getAcknowledgementDate()));
        detail.setSignedInvoice(text(data, "SignedInvoice"));
        detail.setSignedQrCode(text(data, "SignedQRCode"));
        String status = text(data, "Status");
        detail.setStatus("CNL".equalsIgnoreCase(status) ? EInvoiceStatus.CANCELLED : EInvoiceStatus.GENERATED);
        detail.setEwayBillNumber(text(data, "EwbNo"));
        detail.setEwayBillDate(parseDateTime(text(data, "EwbDt"), null));
        detail.setEwayBillValidTill(parseDateTime(text(data, "EwbValidTill"), null));
        detail.setRemarks(text(data, "Remarks"));
        detail.setIrpResponse(toJson(data));
        detail.setErrorCode(null);
        detail.setErrorMessage(null);
    }

    private LocalDateTime parseDateTime(String value, LocalDateTime fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        try { return LocalDateTime.parse(value, ACK_DATE); }
        catch (Exception ignored) { return fallback; }
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (!StringUtils.hasText(value)) throw new IllegalStateException("IRP response is missing " + field);
        return value;
    }

    private String text(JsonNode node, String field) {
        return node == null || node.path(field).isMissingNode() || node.path(field).isNull()
                ? null : node.path(field).asText();
    }

    private String toJson(JsonNode node) {
        if (node == null) return null;
        try { return objectMapper.writeValueAsString(node); }
        catch (Exception exception) { return node.toString(); }
    }

    private String truncate(String value, int size) {
        if (!StringUtils.hasText(value)) return value;
        return value.length() <= size ? value : value.substring(0, size);
    }
}
