package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.AssetManagementRequests.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AssetManagementService {
    Map<String, Object> dashboard(LocalDate from, LocalDate to);

    List<Map<String, Object>> categoryOptions();
    Page<Map<String, Object>> categories(String search, String status, Pageable pageable);
    Map<String, Object> category(Long id);
    Map<String, Object> createCategory(CategoryRequest request);
    Map<String, Object> updateCategory(Long id, CategoryRequest request);
    void deleteCategory(Long id);

    Page<Map<String, Object>> assets(String search, Long categoryId, String status,
                                     String condition, String location, Long assignedResourceId,
                                     LocalDate purchaseFrom, LocalDate purchaseTo,
                                     String ownershipType, String assetOwner, Pageable pageable);
    List<Map<String, Object>> eligibleAssets();
    Map<String, Object> asset(Long id);
    Map<String, Object> createAsset(AssetRequest request);
    Map<String, Object> updateAsset(Long id, AssetRequest request);
    void deleteAsset(Long id);

    Page<Map<String, Object>> assignments(String search, String status, Pageable pageable);
    Map<String, Object> createAssignment(AssignmentRequest request);
    Map<String, Object> returnAssignment(Long id, LocalDate returnDate, String notes);

    Page<Map<String, Object>> maintenance(String search, String status, String type,
                                          String priority, Long technicianId, Long assetId,
                                          LocalDate from, LocalDate to, Pageable pageable);
    Map<String, Object> maintenance(Long id);
    Map<String, Object> createMaintenance(MaintenanceRequest request);
    Map<String, Object> updateMaintenance(Long id, MaintenanceRequest request);
    Map<String, Object> updateMaintenanceStatus(Long id, MaintenanceStatusRequest request);
    void deleteMaintenance(Long id);

    Page<Map<String, Object>> depreciation(String search, String financialYear,
                                           String method, Long categoryId, String status,
                                           Pageable pageable);
    Map<String, Object> depreciation(Long id);
    Map<String, Object> createDepreciation(DepreciationRequest request);
    Map<String, Object> updateDepreciation(Long id, DepreciationRequest request);
    Map<String, Object> runDepreciation(Long id, LocalDate throughDate);

    Page<Map<String, Object>> disposals(String search, LocalDate from, LocalDate to,
                                        String method, Long categoryId, String status,
                                        Pageable pageable);
    Map<String, Object> disposal(Long id);
    Map<String, Object> createDisposal(DisposalRequest request);
    Map<String, Object> updateDisposal(Long id, DisposalRequest request);
    Map<String, Object> updateDisposalStatus(Long id, DisposalStatusRequest request);
    void deleteDisposal(Long id);
}
