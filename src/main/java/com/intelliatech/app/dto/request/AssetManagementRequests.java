package com.intelliatech.app.dto.request;

import com.intelliatech.app.entity.AssetEnums;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class AssetManagementRequests {
    private AssetManagementRequests() {}

    public record CategoryRequest(
            @NotBlank String categoryName,
            String description,
            @NotNull AssetEnums.DepreciationMethod defaultDepreciationMethod,
            @NotNull @Min(1) Integer usefulLifeYears,
            @DecimalMin("0.00") BigDecimal residualValuePercentage,
            @NotNull AssetEnums.Frequency depreciationFrequency,
            @NotNull AssetEnums.AssetCondition defaultAssetCondition,
            String displayColor,
            @NotNull AssetEnums.CategoryStatus status,
            String notes
    ) {}

    public record AssetRequest(
            @NotBlank String assetName,
            @NotNull Long categoryId,
            String subCategory,
            @NotNull AssetEnums.AssetType assetType,
            @NotNull(message = "Please select an Asset Owner.") AssetEnums.AssetOwnerType assetOwner,
            String brand,
            String model,
            String serialNumber,
            String barcode,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            String unit,
            @NotNull AssetEnums.AssetCondition assetCondition,
            AssetEnums.AssetStatus status,
            Long vendorId,
            LocalDate purchaseDate,
            String invoiceNumber,
            String poNumber,
            @DecimalMin("0.00") BigDecimal purchaseValue,
            @DecimalMin("0.00") BigDecimal taxAmount,
            String paymentMethod,
            LocalDate warrantyExpiry,
            String locationName,
            String departmentName,
            String floorRoom,
            String costCenter,
            Long assignedResourceId,
            String assignedResourceName,
            @NotNull AssetEnums.OwnershipType ownershipType,
            LocalDate leaseStartDate,
            LocalDate leaseEndDate,
            String manufacturer,
            Integer manufactureYear,
            String countryOfOrigin,
            String hsnSacCode,
            Integer usefulLifeMonths,
            String notes,
            AssetEnums.DepreciationMethod depreciationMethod,
            LocalDate depreciationStartDate,
            AssetEnums.Frequency depreciationFrequency,
            @DecimalMin("0.00") BigDecimal residualValue,
            @DecimalMin("0.00") BigDecimal scrapValue,
            LocalDate capitalizationDate,
            String imageUrl,
            boolean draft
    ) {}

    public record AssignmentRequest(
            @NotNull Long assetId,
            @NotNull Long resourceId,
            @NotBlank String resourceName,
            @Email String resourceEmail,
            @NotNull LocalDate assignmentDate,
            LocalDate expectedReturnDate,
            String purpose,
            String costCenter,
            String projectName,
            String referenceNumber,
            String notes,
            boolean draft
    ) {}

    public record MaintenanceRequest(
            @NotNull Long assetId,
            @NotBlank String maintenanceType,
            @NotBlank String description,
            String checklistJson,
            Long technicianResourceId,
            String technicianResourceName,
            Long assistantResourceId,
            String assistantResourceName,
            @NotNull LocalDate scheduledDate,
            @NotNull LocalDate dueDate,
            @DecimalMin("0.00") BigDecimal estimatedDurationHours,
            @DecimalMin("0.00") BigDecimal estimatedCost,
            AssetEnums.Frequency repeatFrequency,
            LocalDate nextDueDate,
            @NotNull AssetEnums.MaintenancePriority priority,
            @NotNull AssetEnums.MaintenanceStatus status,
            String notes,
            String attachmentName,
            String attachmentUrl
    ) {}

    public record MaintenanceStatusRequest(
            @NotNull AssetEnums.MaintenanceStatus status,
            LocalDate completedDate,
            @DecimalMin("0.00") BigDecimal actualDurationHours,
            @DecimalMin("0.00") BigDecimal actualCost,
            @DecimalMin("0.00") BigDecimal partsCost,
            @DecimalMin("0.00") BigDecimal labourCost,
            String notes
    ) {}

    public record DepreciationRequest(
            @NotNull Long assetId,
            @NotNull AssetEnums.DepreciationMethod method,
            @NotBlank String financialYear,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull AssetEnums.Frequency frequency,
            @NotNull @Min(1) Integer numberOfPeriods,
            @NotNull AssetEnums.ResidualValueType residualValueType,
            @NotNull @DecimalMin("0.00") BigDecimal residualValue,
            @NotBlank String expenseAccount,
            @NotBlank String accumulatedAccount,
            String proRataConvention,
            boolean depreciateInPurchaseMonth,
            boolean includeInRun,
            String description,
            boolean draft
    ) {}

    public record DisposalRequest(
            @NotNull Long assetId,
            @NotNull LocalDate disposalDate,
            @NotNull AssetEnums.DisposalMethod disposalMethod,
            Long buyerVendorId,
            String buyerVendorName,
            String referenceNumber,
            String reason,
            String gainLossAccount,
            @DecimalMin("0.00") BigDecimal disposalValue,
            String remarks,
            boolean draft
    ) {}

    public record DisposalStatusRequest(
            @NotNull AssetEnums.DisposalStatus status,
            String notes
    ) {}
}
