package com.intelliatech.app.entity;

public final class AssetEnums {
    private AssetEnums() {}

    public enum AssetStatus { DRAFT, AVAILABLE, IN_USE, UNDER_MAINTENANCE, RETIRED, DISPOSED, LOST, WRITTEN_OFF }
    public enum AssetCondition { NEW, GOOD, FAIR, POOR, DAMAGED }
    public enum AssetType { TANGIBLE, INTANGIBLE }
    public enum AssetOwnerType { INTERNAL, CLIENT }
    public enum OwnershipType { OWNED, LEASED }
    public enum DepreciationMethod { STRAIGHT_LINE, WRITTEN_DOWN_VALUE }
    public enum Frequency { MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY, NONE }
    public enum CategoryStatus { ACTIVE, INACTIVE }
    public enum AssignmentStatus { DRAFT, ACTIVE, RETURNED, TRANSFERRED, CANCELLED }
    public enum MaintenanceStatus { DRAFT, SCHEDULED, DUE_TODAY, OVERDUE, IN_PROGRESS, COMPLETED, CANCELLED }
    public enum MaintenancePriority { LOW, MEDIUM, HIGH, CRITICAL }
    public enum DepreciationStatus { DRAFT, SCHEDULED, ACTIVE, COMPLETED, CANCELLED }
    public enum DepreciationEntryStatus { PENDING, POSTED, REVERSED }
    public enum ResidualValueType { FIXED_AMOUNT, PERCENTAGE }
    public enum DisposalStatus { DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, COMPLETED, REVERSED }
    public enum DisposalMethod { SOLD, SCRAPPED, DONATED, TRADED_IN, LOST, WRITTEN_OFF, RETURNED_TO_VENDOR }
}
