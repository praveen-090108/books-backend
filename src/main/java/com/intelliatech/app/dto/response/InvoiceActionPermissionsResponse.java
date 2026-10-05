package com.intelliatech.app.dto.response;

public record InvoiceActionPermissionsResponse(
        boolean edit,
        boolean send,
        boolean recordPayment,
        boolean reminder,
        boolean createCreditNote,
        boolean voidInvoice,
        boolean deleteInvoice,
        boolean cloneInvoice
) {
}
