package com.intelliatech.app.entity;

public enum InvoiceStatus {
    DRAFT,
    SENT,
    VIEWED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    VOID;

    public String displayName() {
        return switch (this) {
            case PARTIALLY_PAID -> "Partially Paid";
            case DRAFT -> "Draft";
            case SENT -> "Sent";
            case VIEWED -> "Viewed";
            case PAID -> "Paid";
            case OVERDUE -> "Overdue";
            case VOID -> "Void";
        };
    }
}
