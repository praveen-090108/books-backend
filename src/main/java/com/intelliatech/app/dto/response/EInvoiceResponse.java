package com.intelliatech.app.dto.response;

import com.intelliatech.app.entity.EInvoiceStatus;
import com.intelliatech.app.entity.IrpEnvironment;
import java.time.LocalDateTime;

public record EInvoiceResponse(
        Long invoiceId,
        Long creditNoteId,
        String irpProvider,
        IrpEnvironment irpEnvironment,
        String irpGstin,
        EInvoiceStatus status,
        String irn,
        String acknowledgementNumber,
        LocalDateTime acknowledgementDate,
        String signedQrCode,
        String qrCodeDataUrl,
        LocalDateTime generatedAt,
        LocalDateTime cancelledAt,
        String ewayBillNumber,
        LocalDateTime ewayBillDate,
        LocalDateTime ewayBillValidTill,
        String remarks,
        String errorCode,
        String errorMessage
) {
}
