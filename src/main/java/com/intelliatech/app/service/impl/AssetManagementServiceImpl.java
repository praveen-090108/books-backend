package com.intelliatech.app.service.impl;

import com.intelliatech.app.dto.request.AssetManagementRequests.*;
import com.intelliatech.app.entity.*;
import com.intelliatech.app.entity.AssetEnums.*;
import com.intelliatech.app.exception.ResourceConflictException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.*;
import com.intelliatech.app.service.AssetManagementService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AssetManagementServiceImpl implements AssetManagementService {
    private static final Long ORGANIZATION_ID = 1L;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository categoryRepository;
    private final AssetAssignmentRepository assignmentRepository;
    private final AssetMaintenanceRepository maintenanceRepository;
    private final AssetDepreciationScheduleRepository depreciationRepository;
    private final AssetDepreciationEntryRepository depreciationEntryRepository;
    private final AssetDisposalRepository disposalRepository;
    private final AssetHistoryRepository historyRepository;
    private final VendorRepository vendorRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(LocalDate from, LocalDate to) {
        long total = assetRepository.countByOrganizationIdAndDeletedFalseAndDraftFalse(ORGANIZATION_ID);
        long inUse = countAsset(AssetStatus.IN_USE);
        long maintenance = countAsset(AssetStatus.UNDER_MAINTENANCE);
        long retired = countAsset(AssetStatus.RETIRED);
        long disposed = countAsset(AssetStatus.DISPOSED);
        List<Asset> assets = assetRepository.findAll(baseAssetSpec().and((root, query, cb) -> cb.isFalse(root.get("draft"))));

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        assets.forEach(asset -> {
            statusCounts.merge(asset.getStatus().name(), 1L, Long::sum);
            String category = asset.getCategory() == null ? "Uncategorized" : asset.getCategory().getCategoryName();
            categoryCounts.merge(category, 1L, Long::sum);
        });

        List<Map<String, Object>> recentAssets = assets.stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .limit(5)
                .map(this::assetSummary)
                .toList();
        List<Map<String, Object>> upcoming = maintenanceRepository
                .findAllByOrganizationIdAndDueDateGreaterThanEqualAndStatusNotInAndDeletedFalseOrderByDueDate(
                        ORGANIZATION_ID,
                        LocalDate.now(),
                        List.of(MaintenanceStatus.COMPLETED, MaintenanceStatus.CANCELLED),
                        Pageable.ofSize(5))
                .stream().map(this::maintenanceSummary).toList();

        return map(
                "from", from,
                "to", to,
                "totalAssets", total,
                "totalValue", money(assetRepository.sumCurrentValue(ORGANIZATION_ID)),
                "inUse", inUse,
                "underMaintenance", maintenance,
                "retiredDisposed", retired + disposed,
                "statusOverview", chart(statusCounts),
                "categoryOverview", chart(categoryCounts),
                "valueTrend", assetValueTrend(assets, from, to),
                "recentAssets", recentAssets,
                "upcomingMaintenance", upcoming,
                "assetHealth", total == 0 ? ZERO : BigDecimal.valueOf(inUse).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP),
                "accumulatedDepreciation", money(assetRepository.sumAccumulatedDepreciation(ORGANIZATION_ID)),
                "netBookValue", money(assetRepository.sumNetBookValue(ORGANIZATION_ID)),
                "insight", total == 0 ? "Add your first asset to start tracking value and utilization."
                        : inUse + " of " + total + " assets are currently in use."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> categoryOptions() {
        return categoryRepository.findAllByOrganizationIdAndStatusAndDeletedFalseOrderByCategoryName(
                        ORGANIZATION_ID, CategoryStatus.ACTIVE)
                .stream().map(this::categorySummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> categories(String search, String status, Pageable pageable) {
        Specification<AssetCategory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), ORGANIZATION_ID));
            predicates.add(cb.isFalse(root.get("deleted")));
            if (StringUtils.hasText(search)) {
                String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("categoryName")), value),
                        cb.like(cb.lower(root.get("categoryCode")), value)));
            }
            CategoryStatus parsed = enumValue(CategoryStatus.class, status);
            if (parsed != null) predicates.add(cb.equal(root.get("status"), parsed));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return categoryRepository.findAll(spec, pageable).map(this::categorySummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> category(Long id) {
        return categoryDetails(findCategory(id));
    }

    @Override
    @Transactional
    public Map<String, Object> createCategory(CategoryRequest request) {
        if (categoryRepository.existsByOrganizationIdAndCategoryNameIgnoreCaseAndDeletedFalse(
                ORGANIZATION_ID, request.categoryName().trim())) {
            throw new ResourceConflictException("An asset category with this name already exists.");
        }
        AssetCategory category = new AssetCategory();
        category.setOrganizationId(ORGANIZATION_ID);
        category.setCategoryCode(nextCategoryCode(request.categoryName()));
        applyCategory(category, request);
        return categoryDetails(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public Map<String, Object> updateCategory(Long id, CategoryRequest request) {
        AssetCategory category = findCategory(id);
        if (categoryRepository.existsByOrganizationIdAndCategoryNameIgnoreCaseAndIdNotAndDeletedFalse(
                ORGANIZATION_ID, request.categoryName().trim(), id)) {
            throw new ResourceConflictException("An asset category with this name already exists.");
        }
        applyCategory(category, request);
        return categoryDetails(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        AssetCategory category = findCategory(id);
        if (assetRepository.countByOrganizationIdAndCategoryIdAndDeletedFalse(ORGANIZATION_ID, id) > 0) {
            throw new ResourceConflictException("This category cannot be deleted because assets are linked to it.");
        }
        category.setDeleted(true);
        categoryRepository.save(category);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> assets(String search, Long categoryId, String status, String condition,
                                             String location, Long assignedResourceId, LocalDate purchaseFrom,
                                             LocalDate purchaseTo, String ownershipType, String assetOwner,
                                             Pageable pageable) {
        AssetOwnerType parsedAssetOwner = enumValue(AssetOwnerType.class, assetOwner);
        if (StringUtils.hasText(assetOwner) && !"ALL".equalsIgnoreCase(assetOwner) && parsedAssetOwner == null) {
            throw new IllegalArgumentException("Asset Owner must be INTERNAL or CLIENT.");
        }
        Specification<Asset> spec = baseAssetSpec().and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(search)) {
                String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("assetName")), value),
                        cb.like(cb.lower(root.get("assetNumber")), value),
                        cb.like(cb.lower(root.get("serialNumber")), value),
                        cb.like(cb.lower(root.get("barcode")), value)));
            }
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            AssetStatus parsedStatus = enumValue(AssetStatus.class, status);
            if (parsedStatus != null) predicates.add(cb.equal(root.get("status"), parsedStatus));
            AssetCondition parsedCondition = enumValue(AssetCondition.class, condition);
            if (parsedCondition != null) predicates.add(cb.equal(root.get("assetCondition"), parsedCondition));
            if (StringUtils.hasText(location)) predicates.add(cb.equal(cb.lower(root.get("locationName")), location.toLowerCase(Locale.ROOT)));
            if (assignedResourceId != null) predicates.add(cb.equal(root.get("assignedResourceId"), assignedResourceId));
            if (purchaseFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), purchaseFrom));
            if (purchaseTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), purchaseTo));
            OwnershipType ownership = enumValue(OwnershipType.class, ownershipType);
            if (ownership != null) predicates.add(cb.equal(root.get("ownershipType"), ownership));
            if (parsedAssetOwner != null) predicates.add(cb.equal(root.get("assetOwner"), parsedAssetOwner));
            return cb.and(predicates.toArray(Predicate[]::new));
        });
        return assetRepository.findAll(spec, pageable).map(this::assetSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> eligibleAssets() {
        return assetRepository.findAllByOrganizationIdAndStatusInAndDeletedFalseOrderByAssetName(
                        ORGANIZATION_ID, List.of(AssetStatus.AVAILABLE, AssetStatus.IN_USE))
                .stream().filter(asset -> assignmentRepository.findFirstByOrganizationIdAndAssetIdAndStatus(
                        ORGANIZATION_ID, asset.getId(), AssignmentStatus.ACTIVE).isEmpty())
                .map(this::assetSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> asset(Long id) {
        Asset asset = findAsset(id);
        Map<String, Object> details = assetDetails(asset);
        details.put("assignments", assignmentRepository.findAllByOrganizationIdAndAssetIdOrderByCreatedAtDesc(ORGANIZATION_ID, id)
                .stream().map(this::assignmentSummary).toList());
        details.put("maintenance", maintenanceRepository.findAllByOrganizationIdAndAssetIdAndDeletedFalseOrderByScheduledDateDesc(
                ORGANIZATION_ID, id).stream().map(this::maintenanceSummary).toList());
        details.put("depreciationSchedules", depreciationRepository.findAllByOrganizationIdAndAssetIdAndDeletedFalseOrderByCreatedAtDesc(
                ORGANIZATION_ID, id).stream().map(this::depreciationSummary).toList());
        details.put("history", historyRepository.findAllByAssetIdOrderByCreatedAtDesc(id).stream().map(this::historySummary).toList());
        return details;
    }

    @Override
    @Transactional
    public Map<String, Object> createAsset(AssetRequest request) {
        AssetCategory category = findCategory(request.categoryId());
        Asset asset = new Asset();
        asset.setOrganizationId(ORGANIZATION_ID);
        asset.setAssetNumber(nextAssetNumber(category));
        applyAsset(asset, category, request);
        asset = assetRepository.save(asset);
        ensureDepreciationSchedule(asset);
        history(asset, "CREATED", null, asset.getStatus(), "Asset created");
        return assetDetails(asset);
    }

    @Override
    @Transactional
    public Map<String, Object> updateAsset(Long id, AssetRequest request) {
        Asset asset = findAsset(id);
        AssetStatus previous = asset.getStatus();
        applyAsset(asset, findCategory(request.categoryId()), request);
        asset = assetRepository.save(asset);
        ensureDepreciationSchedule(asset);
        history(asset, "UPDATED", previous, asset.getStatus(), "Asset details updated");
        return assetDetails(asset);
    }

    @Override
    @Transactional
    public void deleteAsset(Long id) {
        Asset asset = findAsset(id);
        if (assignmentRepository.findFirstByOrganizationIdAndAssetIdAndStatus(ORGANIZATION_ID, id, AssignmentStatus.ACTIVE).isPresent()) {
            throw new ResourceConflictException("Return the assigned asset before deleting it.");
        }
        asset.setDeleted(true);
        assetRepository.save(asset);
        history(asset, "DELETED", asset.getStatus(), asset.getStatus(), "Asset deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> assignments(String search, String status, Pageable pageable) {
        Specification<AssetAssignment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), ORGANIZATION_ID));
            if (StringUtils.hasText(search)) {
                String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("resourceName")), value),
                        cb.like(cb.lower(root.get("asset").get("assetName")), value),
                        cb.like(cb.lower(root.get("asset").get("assetNumber")), value)));
            }
            AssignmentStatus parsed = enumValue(AssignmentStatus.class, status);
            if (parsed != null) predicates.add(cb.equal(root.get("status"), parsed));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return assignmentRepository.findAll(spec, pageable).map(this::assignmentSummary);
    }

    @Override
    @Transactional
    public Map<String, Object> createAssignment(AssignmentRequest request) {
        Asset asset = findAsset(request.assetId());
        if (List.of(AssetStatus.DISPOSED, AssetStatus.RETIRED, AssetStatus.LOST, AssetStatus.WRITTEN_OFF,
                AssetStatus.UNDER_MAINTENANCE).contains(asset.getStatus())) {
            throw new IllegalStateException("This asset is not eligible for assignment.");
        }
        if (assignmentRepository.findFirstByOrganizationIdAndAssetIdAndStatus(
                ORGANIZATION_ID, asset.getId(), AssignmentStatus.ACTIVE).isPresent()) {
            throw new ResourceConflictException("This asset already has an active assignment.");
        }
        AssetAssignment assignment = new AssetAssignment();
        assignment.setOrganizationId(ORGANIZATION_ID);
        assignment.setAsset(asset);
        assignment.setResourceId(request.resourceId());
        assignment.setResourceName(request.resourceName().trim());
        assignment.setResourceEmail(trim(request.resourceEmail()));
        assignment.setAssignmentDate(request.assignmentDate());
        assignment.setExpectedReturnDate(request.expectedReturnDate());
        assignment.setPurpose(trim(request.purpose()));
        assignment.setCostCenter(trim(request.costCenter()));
        assignment.setProjectName(trim(request.projectName()));
        assignment.setReferenceNumber(trim(request.referenceNumber()));
        assignment.setNotes(trim(request.notes()));
        assignment.setStatus(request.draft() ? AssignmentStatus.DRAFT : AssignmentStatus.ACTIVE);
        assignment = assignmentRepository.save(assignment);
        if (!request.draft()) {
            AssetStatus previous = asset.getStatus();
            asset.setStatus(AssetStatus.IN_USE);
            asset.setAssignedResourceId(request.resourceId());
            asset.setAssignedResourceName(request.resourceName().trim());
            assetRepository.save(asset);
            history(asset, "ASSIGNED", previous, AssetStatus.IN_USE, "Assigned to " + request.resourceName());
        }
        return assignmentSummary(assignment);
    }

    @Override
    @Transactional
    public Map<String, Object> returnAssignment(Long id, LocalDate returnDate, String notes) {
        AssetAssignment assignment = assignmentRepository.findByIdAndOrganizationId(id, ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Asset assignment not found."));
        if (assignment.getStatus() != AssignmentStatus.ACTIVE) throw new IllegalStateException("Only active assignments can be returned.");
        assignment.setActualReturnDate(returnDate == null ? LocalDate.now() : returnDate);
        assignment.setStatus(AssignmentStatus.RETURNED);
        if (StringUtils.hasText(notes)) assignment.setNotes(notes.trim());
        assignmentRepository.save(assignment);
        Asset asset = assignment.getAsset();
        AssetStatus previous = asset.getStatus();
        asset.setAssignedResourceId(null);
        asset.setAssignedResourceName(null);
        asset.setStatus(AssetStatus.AVAILABLE);
        assetRepository.save(asset);
        history(asset, "RETURNED", previous, AssetStatus.AVAILABLE, "Asset returned");
        return assignmentSummary(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> maintenance(String search, String status, String type, String priority,
                                                  Long technicianId, Long assetId, LocalDate from, LocalDate to,
                                                  Pageable pageable) {
        Specification<AssetMaintenance> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), ORGANIZATION_ID));
            predicates.add(cb.isFalse(root.get("deleted")));
            if (StringUtils.hasText(search)) {
                String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("workOrderNumber")), value),
                        cb.like(cb.lower(root.get("asset").get("assetName")), value),
                        cb.like(cb.lower(root.get("technicianResourceName")), value)));
            }
            MaintenanceStatus parsedStatus = enumValue(MaintenanceStatus.class, status);
            if (parsedStatus != null) predicates.add(cb.equal(root.get("status"), parsedStatus));
            if (StringUtils.hasText(type)) predicates.add(cb.equal(cb.lower(root.get("maintenanceType")), type.toLowerCase(Locale.ROOT)));
            MaintenancePriority parsedPriority = enumValue(MaintenancePriority.class, priority);
            if (parsedPriority != null) predicates.add(cb.equal(root.get("priority"), parsedPriority));
            if (technicianId != null) predicates.add(cb.equal(root.get("technicianResourceId"), technicianId));
            if (assetId != null) predicates.add(cb.equal(root.get("asset").get("id"), assetId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return maintenanceRepository.findAll(spec, pageable).map(this::maintenanceSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> maintenance(Long id) {
        return maintenanceDetails(findMaintenance(id));
    }

    @Override
    @Transactional
    public Map<String, Object> createMaintenance(MaintenanceRequest request) {
        AssetMaintenance maintenance = new AssetMaintenance();
        maintenance.setOrganizationId(ORGANIZATION_ID);
        maintenance.setWorkOrderNumber("WO-" + LocalDate.now().getYear() + "-" + String.format("%04d", maintenanceRepository.count() + 1));
        applyMaintenance(maintenance, findAsset(request.assetId()), request);
        maintenance = maintenanceRepository.save(maintenance);
        history(maintenance.getAsset(), "MAINTENANCE_CREATED", maintenance.getAsset().getStatus(),
                maintenance.getAsset().getStatus(), maintenance.getWorkOrderNumber());
        return maintenanceDetails(maintenance);
    }

    @Override
    @Transactional
    public Map<String, Object> updateMaintenance(Long id, MaintenanceRequest request) {
        AssetMaintenance maintenance = findMaintenance(id);
        applyMaintenance(maintenance, findAsset(request.assetId()), request);
        return maintenanceDetails(maintenanceRepository.save(maintenance));
    }

    @Override
    @Transactional
    public Map<String, Object> updateMaintenanceStatus(Long id, MaintenanceStatusRequest request) {
        AssetMaintenance maintenance = findMaintenance(id);
        MaintenanceStatus previous = maintenance.getStatus();
        maintenance.setStatus(request.status());
        maintenance.setCompletedDate(request.completedDate());
        maintenance.setActualDurationHours(money(request.actualDurationHours()));
        maintenance.setActualCost(money(request.actualCost()));
        maintenance.setPartsCost(money(request.partsCost()));
        maintenance.setLabourCost(money(request.labourCost()));
        if (StringUtils.hasText(request.notes())) maintenance.setNotes(request.notes().trim());
        Asset asset = maintenance.getAsset();
        AssetStatus assetPrevious = asset.getStatus();
        if (request.status() == MaintenanceStatus.IN_PROGRESS) asset.setStatus(AssetStatus.UNDER_MAINTENANCE);
        if (request.status() == MaintenanceStatus.COMPLETED || request.status() == MaintenanceStatus.CANCELLED) {
            asset.setStatus(asset.getAssignedResourceId() == null ? AssetStatus.AVAILABLE : AssetStatus.IN_USE);
        }
        assetRepository.save(asset);
        maintenanceRepository.save(maintenance);
        history(asset, "MAINTENANCE_" + request.status().name(), assetPrevious, asset.getStatus(),
                previous + " to " + request.status());
        return maintenanceDetails(maintenance);
    }

    @Override
    @Transactional
    public void deleteMaintenance(Long id) {
        AssetMaintenance maintenance = findMaintenance(id);
        if (maintenance.getStatus() == MaintenanceStatus.COMPLETED) throw new ResourceConflictException("Completed maintenance cannot be deleted.");
        maintenance.setDeleted(true);
        maintenanceRepository.save(maintenance);
    }

    @Override
    @Transactional
    public Page<Map<String, Object>> depreciation(String search, String financialYear, String method,
                                                   Long categoryId, String status, Pageable pageable) {
        assetRepository.findAllByOrganizationIdAndStatusInAndDeletedFalseOrderByAssetName(
                        ORGANIZATION_ID, List.of(AssetStatus.AVAILABLE, AssetStatus.IN_USE, AssetStatus.UNDER_MAINTENANCE))
                .forEach(this::ensureDepreciationSchedule);
        Specification<AssetDepreciationSchedule> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), ORGANIZATION_ID));
            predicates.add(cb.isFalse(root.get("deleted")));
            if (StringUtils.hasText(search)) {
                String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("asset").get("assetName")), value),
                        cb.like(cb.lower(root.get("asset").get("assetNumber")), value)));
            }
            if (StringUtils.hasText(financialYear)) predicates.add(cb.equal(root.get("financialYear"), financialYear));
            DepreciationMethod parsedMethod = enumValue(DepreciationMethod.class, method);
            if (parsedMethod != null) predicates.add(cb.equal(root.get("method"), parsedMethod));
            if (categoryId != null) predicates.add(cb.equal(root.get("asset").get("category").get("id"), categoryId));
            DepreciationStatus parsedStatus = enumValue(DepreciationStatus.class, status);
            if (parsedStatus != null) predicates.add(cb.equal(root.get("status"), parsedStatus));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return depreciationRepository.findAll(spec, normalizeDepreciationPageable(pageable)).map(this::depreciationSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> depreciation(Long id) {
        AssetDepreciationSchedule schedule = findDepreciation(id);
        Map<String, Object> details = depreciationDetails(schedule);
        details.put("entries", depreciationEntryRepository.findAllByScheduleIdOrderByPeriodNumber(id)
                .stream().map(this::entrySummary).toList());
        return details;
    }

    @Override
    @Transactional
    public Map<String, Object> createDepreciation(DepreciationRequest request) {
        Asset asset = findAsset(request.assetId());
        if (List.of(AssetStatus.DISPOSED, AssetStatus.WRITTEN_OFF).contains(asset.getStatus())) {
            throw new IllegalStateException("Depreciation cannot be scheduled for this asset.");
        }
        AssetDepreciationSchedule schedule = new AssetDepreciationSchedule();
        schedule.setOrganizationId(ORGANIZATION_ID);
        applyDepreciation(schedule, asset, request);
        schedule = depreciationRepository.save(schedule);
        rebuildEntries(schedule);
        history(asset, "DEPRECIATION_SCHEDULED", asset.getStatus(), asset.getStatus(), request.financialYear());
        return depreciationDetails(schedule);
    }

    @Override
    @Transactional
    public Map<String, Object> updateDepreciation(Long id, DepreciationRequest request) {
        AssetDepreciationSchedule schedule = findDepreciation(id);
        applyDepreciation(schedule, findAsset(request.assetId()), request);
        schedule = depreciationRepository.save(schedule);
        rebuildEntries(schedule);
        return depreciationDetails(schedule);
    }

    @Override
    @Transactional
    public Map<String, Object> runDepreciation(Long id, LocalDate throughDate) {
        AssetDepreciationSchedule schedule = findDepreciation(id);
        if (schedule.getStatus() == DepreciationStatus.DRAFT || schedule.getStatus() == DepreciationStatus.CANCELLED) {
            throw new IllegalStateException("Activate the depreciation schedule before running it.");
        }
        LocalDate cutoff = throughDate == null ? LocalDate.now() : throughDate;
        List<AssetDepreciationEntry> entries = depreciationEntryRepository.findAllByScheduleIdOrderByPeriodNumber(id);
        BigDecimal accumulated = ZERO;
        for (AssetDepreciationEntry entry : entries) {
            if (!entry.getEndDate().isAfter(cutoff) && entry.getStatus() == DepreciationEntryStatus.PENDING) {
                entry.setStatus(DepreciationEntryStatus.POSTED);
                entry.setPostedAt(LocalDateTime.now());
            }
            if (entry.getStatus() == DepreciationEntryStatus.POSTED) accumulated = accumulated.add(entry.getDepreciationAmount());
        }
        depreciationEntryRepository.saveAll(entries);
        Asset asset = schedule.getAsset();
        BigDecimal capped = accumulated.min(schedule.getDepreciableAmount());
        asset.setAccumulatedDepreciation(capped);
        asset.setNetBookValue(money(asset.getPurchaseValue()).subtract(capped).max(schedule.getResidualValue()));
        asset.setCurrentValue(asset.getNetBookValue());
        assetRepository.save(asset);
        schedule.setLastRunAt(LocalDateTime.now());
        schedule.setNextRunDate(entries.stream().filter(entry -> entry.getStatus() == DepreciationEntryStatus.PENDING)
                .map(AssetDepreciationEntry::getEndDate).findFirst().orElse(null));
        if (schedule.getNextRunDate() == null) schedule.setStatus(DepreciationStatus.COMPLETED);
        depreciationRepository.save(schedule);
        history(asset, "DEPRECIATION_RUN", asset.getStatus(), asset.getStatus(), "Accumulated " + capped);
        return depreciation(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> disposals(String search, LocalDate from, LocalDate to, String method,
                                                Long categoryId, String status, Pageable pageable) {
        Specification<AssetDisposal> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), ORGANIZATION_ID));
            predicates.add(cb.isFalse(root.get("deleted")));
            if (StringUtils.hasText(search)) {
                String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("disposalNumber")), value),
                        cb.like(cb.lower(root.get("asset").get("assetName")), value),
                        cb.like(cb.lower(root.get("asset").get("assetNumber")), value)));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("disposalDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("disposalDate"), to));
            DisposalMethod parsedMethod = enumValue(DisposalMethod.class, method);
            if (parsedMethod != null) predicates.add(cb.equal(root.get("disposalMethod"), parsedMethod));
            if (categoryId != null) predicates.add(cb.equal(root.get("asset").get("category").get("id"), categoryId));
            DisposalStatus parsedStatus = enumValue(DisposalStatus.class, status);
            if (parsedStatus != null) predicates.add(cb.equal(root.get("status"), parsedStatus));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return disposalRepository.findAll(spec, pageable).map(this::disposalSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> disposal(Long id) {
        return disposalDetails(findDisposal(id));
    }

    @Override
    @Transactional
    public Map<String, Object> createDisposal(DisposalRequest request) {
        validateDisposalRequest(request);
        Asset asset = findAsset(request.assetId());
        if (!List.of(AssetStatus.AVAILABLE, AssetStatus.IN_USE, AssetStatus.UNDER_MAINTENANCE).contains(asset.getStatus())) {
            throw new IllegalStateException("This asset is not eligible for disposal.");
        }
        if (disposalRepository.findFirstByOrganizationIdAndAssetIdAndStatusNotAndDeletedFalse(
                ORGANIZATION_ID, asset.getId(), DisposalStatus.REVERSED).isPresent()) {
            throw new ResourceConflictException("An active disposal already exists for this asset.");
        }
        AssetDisposal disposal = new AssetDisposal();
        disposal.setOrganizationId(ORGANIZATION_ID);
        disposal.setDisposalNumber("DISP-" + LocalDate.now().getYear() + "-" + String.format("%04d", disposalRepository.count() + 1));
        applyDisposal(disposal, asset, request);
        disposal = disposalRepository.save(disposal);
        history(asset, "DISPOSAL_CREATED", asset.getStatus(), asset.getStatus(), disposal.getDisposalNumber());
        return disposalDetails(disposal);
    }

    @Override
    @Transactional
    public Map<String, Object> updateDisposal(Long id, DisposalRequest request) {
        AssetDisposal disposal = findDisposal(id);
        if (disposal.getStatus() == DisposalStatus.COMPLETED) throw new ResourceConflictException("Reverse the completed disposal before editing it.");
        validateDisposalRequest(request);
        applyDisposal(disposal, findAsset(request.assetId()), request);
        return disposalDetails(disposalRepository.save(disposal));
    }

    @Override
    @Transactional
    public Map<String, Object> updateDisposalStatus(Long id, DisposalStatusRequest request) {
        AssetDisposal disposal = findDisposal(id);
        Asset asset = disposal.getAsset();
        DisposalStatus previous = disposal.getStatus();
        if (request.status() == DisposalStatus.COMPLETED) {
            if (previous != DisposalStatus.APPROVED && previous != DisposalStatus.PENDING_APPROVAL) {
                throw new IllegalStateException("Only an approved or pending disposal can be completed.");
            }
            assignmentRepository.findFirstByOrganizationIdAndAssetIdAndStatus(ORGANIZATION_ID, asset.getId(), AssignmentStatus.ACTIVE)
                    .ifPresent(active -> returnAssignment(active.getId(), LocalDate.now(), "Returned during disposal"));
            AssetStatus oldStatus = asset.getStatus();
            asset.setStatus(AssetStatus.DISPOSED);
            asset.setAssignedResourceId(null);
            asset.setAssignedResourceName(null);
            assetRepository.save(asset);
            disposal.setCompletedAt(LocalDateTime.now());
            history(asset, "DISPOSAL_COMPLETED", oldStatus, AssetStatus.DISPOSED, request.notes());
        } else if (request.status() == DisposalStatus.REVERSED) {
            if (previous != DisposalStatus.COMPLETED) throw new IllegalStateException("Only completed disposals can be reversed.");
            AssetStatus oldStatus = asset.getStatus();
            asset.setStatus(AssetStatus.AVAILABLE);
            assetRepository.save(asset);
            disposal.setReversedAt(LocalDateTime.now());
            history(asset, "DISPOSAL_REVERSED", oldStatus, AssetStatus.AVAILABLE, request.notes());
        } else if (request.status() == DisposalStatus.APPROVED) {
            disposal.setApprovedAt(LocalDateTime.now());
            disposal.setApprovedBy("Admin");
            disposal.setApprovalNotes(trim(request.notes()));
        }
        disposal.setStatus(request.status());
        disposalRepository.save(disposal);
        return disposalDetails(disposal);
    }

    @Override
    @Transactional
    public void deleteDisposal(Long id) {
        AssetDisposal disposal = findDisposal(id);
        if (disposal.getStatus() == DisposalStatus.COMPLETED) throw new ResourceConflictException("Completed disposals must be reversed before deletion.");
        disposal.setDeleted(true);
        disposalRepository.save(disposal);
    }

    private void applyCategory(AssetCategory category, CategoryRequest request) {
        category.setCategoryName(request.categoryName().trim());
        category.setDescription(trim(request.description()));
        category.setDefaultDepreciationMethod(request.defaultDepreciationMethod());
        category.setUsefulLifeYears(request.usefulLifeYears());
        category.setResidualValuePercentage(money(request.residualValuePercentage()));
        category.setDepreciationFrequency(request.depreciationFrequency());
        category.setDefaultAssetCondition(request.defaultAssetCondition());
        category.setDisplayColor(StringUtils.hasText(request.displayColor()) ? request.displayColor().trim() : "#ef233c");
        category.setStatus(request.status());
        category.setNotes(trim(request.notes()));
    }

    private void applyAsset(Asset asset, AssetCategory category, AssetRequest request) {
        asset.setCategory(category);
        asset.setAssetName(request.assetName().trim());
        asset.setSubCategory(trim(request.subCategory()));
        asset.setAssetType(request.assetType());
        asset.setAssetOwner(request.assetOwner());
        asset.setBrand(trim(request.brand()));
        asset.setModel(trim(request.model()));
        asset.setSerialNumber(trim(request.serialNumber()));
        asset.setBarcode(trim(request.barcode()));
        asset.setQuantity(request.quantity());
        asset.setUnit(trim(request.unit()));
        asset.setAssetCondition(request.assetCondition());
        asset.setStatus(request.draft() ? AssetStatus.DRAFT : Objects.requireNonNullElse(request.status(), AssetStatus.AVAILABLE));
        asset.setDraft(request.draft());
        asset.setVendor(request.vendorId() == null ? null : vendorRepository.findByIdAndOrganizationId(request.vendorId(), ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found.")));
        asset.setVendorName(asset.getVendor() == null ? null : asset.getVendor().getVendorName());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setInvoiceNumber(trim(request.invoiceNumber()));
        asset.setPoNumber(trim(request.poNumber()));
        asset.setPurchaseValue(money(request.purchaseValue()));
        asset.setTaxAmount(money(request.taxAmount()));
        asset.setTotalAmount(asset.getPurchaseValue().add(asset.getTaxAmount()));
        asset.setPaymentMethod(trim(request.paymentMethod()));
        asset.setWarrantyExpiry(request.warrantyExpiry());
        asset.setLocationName(trim(request.locationName()));
        asset.setDepartmentName(trim(request.departmentName()));
        asset.setFloorRoom(trim(request.floorRoom()));
        asset.setCostCenter(trim(request.costCenter()));
        asset.setAssignedResourceId(request.assignedResourceId());
        asset.setAssignedResourceName(trim(request.assignedResourceName()));
        asset.setOwnershipType(request.ownershipType());
        asset.setLeaseStartDate(request.ownershipType() == OwnershipType.LEASED ? request.leaseStartDate() : null);
        asset.setLeaseEndDate(request.ownershipType() == OwnershipType.LEASED ? request.leaseEndDate() : null);
        asset.setManufacturer(trim(request.manufacturer()));
        asset.setManufactureYear(request.manufactureYear());
        asset.setCountryOfOrigin(trim(request.countryOfOrigin()));
        asset.setHsnSacCode(trim(request.hsnSacCode()));
        asset.setUsefulLifeMonths(request.usefulLifeMonths() == null ? category.getUsefulLifeYears() * 12 : request.usefulLifeMonths());
        asset.setNotes(trim(request.notes()));
        asset.setDepreciationMethod(Objects.requireNonNullElse(request.depreciationMethod(), category.getDefaultDepreciationMethod()));
        asset.setDepreciationStartDate(request.depreciationStartDate());
        asset.setDepreciationFrequency(Objects.requireNonNullElse(request.depreciationFrequency(), category.getDepreciationFrequency()));
        asset.setResidualValue(money(request.residualValue()));
        asset.setScrapValue(money(request.scrapValue()));
        asset.setCapitalizationDate(request.capitalizationDate());
        asset.setCurrentValue(asset.getAccumulatedDepreciation() == null || asset.getId() == null
                ? asset.getPurchaseValue() : asset.getCurrentValue());
        asset.setAccumulatedDepreciation(money(asset.getAccumulatedDepreciation()));
        asset.setNetBookValue(asset.getCurrentValue());
        asset.setImageUrl(trim(request.imageUrl()));
    }

    private void applyMaintenance(AssetMaintenance maintenance, Asset asset, MaintenanceRequest request) {
        if (request.dueDate().isBefore(request.scheduledDate())) throw new IllegalArgumentException("Due date cannot be before the scheduled date.");
        maintenance.setAsset(asset);
        maintenance.setMaintenanceType(request.maintenanceType().trim());
        maintenance.setDescription(request.description().trim());
        maintenance.setChecklistJson(trim(request.checklistJson()));
        maintenance.setTechnicianResourceId(request.technicianResourceId());
        maintenance.setTechnicianResourceName(trim(request.technicianResourceName()));
        maintenance.setAssistantResourceId(request.assistantResourceId());
        maintenance.setAssistantResourceName(trim(request.assistantResourceName()));
        maintenance.setScheduledDate(request.scheduledDate());
        maintenance.setDueDate(request.dueDate());
        maintenance.setEstimatedDurationHours(money(request.estimatedDurationHours()));
        maintenance.setEstimatedCost(money(request.estimatedCost()));
        maintenance.setRepeatFrequency(Objects.requireNonNullElse(request.repeatFrequency(), Frequency.NONE));
        maintenance.setNextDueDate(request.nextDueDate());
        maintenance.setPriority(request.priority());
        maintenance.setStatus(request.status());
        maintenance.setNotes(trim(request.notes()));
        maintenance.setAttachmentName(trim(request.attachmentName()));
        maintenance.setAttachmentUrl(trim(request.attachmentUrl()));
    }

    private void applyDepreciation(AssetDepreciationSchedule schedule, Asset asset, DepreciationRequest request) {
        LocalDate endDate = request.endDate() == null
                ? depreciationEndDate(request.startDate(), request.numberOfPeriods(), request.frequency())
                : request.endDate();
        if (endDate.isBefore(request.startDate())) throw new IllegalArgumentException("Depreciation end date must be after the start date.");
        BigDecimal purchaseValue = money(asset.getPurchaseValue());
        BigDecimal residual = request.residualValueType() == ResidualValueType.PERCENTAGE
                ? purchaseValue.multiply(request.residualValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : money(request.residualValue());
        if (residual.compareTo(purchaseValue) > 0) throw new IllegalArgumentException("Residual value cannot exceed purchase value.");
        schedule.setAsset(asset);
        schedule.setMethod(request.method());
        schedule.setFinancialYear(request.financialYear().trim());
        schedule.setStartDate(request.startDate());
        schedule.setEndDate(endDate);
        schedule.setFrequency(request.frequency());
        schedule.setNumberOfPeriods(request.numberOfPeriods());
        schedule.setResidualValueType(request.residualValueType());
        schedule.setResidualValue(residual);
        schedule.setDepreciableAmount(purchaseValue.subtract(residual));
        schedule.setDepreciationPerPeriod(schedule.getDepreciableAmount().divide(
                BigDecimal.valueOf(request.numberOfPeriods()), 2, RoundingMode.HALF_UP));
        schedule.setExpenseAccount(request.expenseAccount().trim());
        schedule.setAccumulatedAccount(request.accumulatedAccount().trim());
        schedule.setProRataConvention(trim(request.proRataConvention()));
        schedule.setDepreciateInPurchaseMonth(request.depreciateInPurchaseMonth());
        schedule.setIncludeInRun(request.includeInRun());
        schedule.setDescription(trim(request.description()));
        schedule.setStatus(request.draft() ? DepreciationStatus.DRAFT : DepreciationStatus.ACTIVE);
        schedule.setNextRunDate(request.startDate());
    }

    private void ensureDepreciationSchedule(Asset asset) {
        if (asset.isDraft() || asset.getDepreciationMethod() == null || asset.getUsefulLifeMonths() == null
                || asset.getUsefulLifeMonths() < 1 || money(asset.getPurchaseValue()).compareTo(ZERO) <= 0
                || List.of(AssetStatus.DRAFT, AssetStatus.RETIRED, AssetStatus.DISPOSED,
                AssetStatus.LOST, AssetStatus.WRITTEN_OFF).contains(asset.getStatus())
                || depreciationRepository.findFirstByOrganizationIdAndAssetIdAndDeletedFalseOrderByCreatedAtDesc(
                ORGANIZATION_ID, asset.getId()).isPresent()) {
            return;
        }

        LocalDate start = asset.getDepreciationStartDate() != null ? asset.getDepreciationStartDate()
                : asset.getCapitalizationDate() != null ? asset.getCapitalizationDate()
                : asset.getPurchaseDate();
        if (start == null) return;
        Frequency frequency = asset.getDepreciationFrequency() == null ? Frequency.MONTHLY : asset.getDepreciationFrequency();
        int months = asset.getUsefulLifeMonths();
        int periods = switch (frequency) {
            case QUARTERLY -> (months + 2) / 3;
            case HALF_YEARLY -> (months + 5) / 6;
            case YEARLY -> (months + 11) / 12;
            case NONE -> 1;
            default -> months;
        };
        BigDecimal residual = money(asset.getResidualValue()).min(money(asset.getPurchaseValue()));
        int fiscalStart = start.getMonthValue() >= 4 ? start.getYear() : start.getYear() - 1;
        String financialYear = fiscalStart + "-" + String.format("%02d", (fiscalStart + 1) % 100);
        DepreciationRequest request = new DepreciationRequest(asset.getId(), asset.getDepreciationMethod(),
                financialYear, start, start.plusMonths(months).minusDays(1), frequency, periods,
                ResidualValueType.FIXED_AMOUNT, residual, "Depreciation Expense", "Accumulated Depreciation",
                "FULL_MONTH", true, true, "Created from asset financial settings", false);
        AssetDepreciationSchedule schedule = new AssetDepreciationSchedule();
        schedule.setOrganizationId(ORGANIZATION_ID);
        applyDepreciation(schedule, asset, request);
        schedule = depreciationRepository.save(schedule);
        rebuildEntries(schedule);
    }

    private void rebuildEntries(AssetDepreciationSchedule schedule) {
        depreciationEntryRepository.deleteAllByScheduleId(schedule.getId());
        List<AssetDepreciationEntry> entries = new ArrayList<>();
        BigDecimal opening = money(schedule.getAsset().getPurchaseValue());
        BigDecimal accumulated = ZERO;
        LocalDate start = schedule.getStartDate();
        for (int index = 1; index <= schedule.getNumberOfPeriods(); index++) {
            LocalDate candidateEnd = periodEnd(start, schedule.getFrequency());
            LocalDate end = candidateEnd.isAfter(schedule.getEndDate()) ? schedule.getEndDate() : candidateEnd;
            BigDecimal depreciation = schedule.getMethod() == DepreciationMethod.WRITTEN_DOWN_VALUE
                    ? opening.subtract(schedule.getResidualValue()).max(ZERO)
                    .divide(BigDecimal.valueOf(schedule.getNumberOfPeriods() - index + 1L), 2, RoundingMode.HALF_UP)
                    : schedule.getDepreciationPerPeriod();
            if (index == schedule.getNumberOfPeriods()) depreciation = schedule.getDepreciableAmount().subtract(accumulated).max(ZERO);
            BigDecimal closing = opening.subtract(depreciation).max(schedule.getResidualValue());
            AssetDepreciationEntry entry = new AssetDepreciationEntry();
            entry.setSchedule(schedule);
            entry.setAsset(schedule.getAsset());
            entry.setFinancialYear(schedule.getFinancialYear());
            entry.setPeriodNumber(index);
            entry.setPeriodLabel("Period " + index);
            entry.setStartDate(start);
            entry.setEndDate(end);
            entry.setOpeningBookValue(opening);
            entry.setDepreciationAmount(depreciation);
            accumulated = accumulated.add(depreciation);
            entry.setAccumulatedDepreciation(accumulated);
            entry.setClosingBookValue(closing);
            entry.setStatus(DepreciationEntryStatus.PENDING);
            entries.add(entry);
            opening = closing;
            start = end.plusDays(1);
        }
        depreciationEntryRepository.saveAll(entries);
    }

    private void validateDisposalRequest(DisposalRequest request) {
        if (request.draft()) return;
        if (!StringUtils.hasText(request.reason())) {
            throw new IllegalArgumentException("Reason for disposal is required.");
        }
        if (request.disposalValue() == null) {
            throw new IllegalArgumentException("Disposal value is required.");
        }
    }

    private Pageable normalizeDepreciationPageable(Pageable pageable) {
        List<Sort.Order> orders = pageable.getSort().stream().map(order -> {
            String property = switch (order.getProperty()) {
                case "asset.name", "assetName", "name" -> "asset.assetName";
                case "assetNumber" -> "asset.assetNumber";
                default -> order.getProperty();
            };
            return new Sort.Order(order.getDirection(), property);
        }).toList();
        Sort sort = orders.isEmpty() ? Sort.by("asset.assetName").ascending() : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private LocalDate depreciationEndDate(LocalDate startDate, int numberOfPeriods, Frequency frequency) {
        int monthsPerPeriod = switch (frequency) {
            case QUARTERLY -> 3;
            case HALF_YEARLY -> 6;
            case YEARLY -> 12;
            default -> 1;
        };
        return startDate.plusMonths((long) numberOfPeriods * monthsPerPeriod).minusDays(1);
    }

    private void applyDisposal(AssetDisposal disposal, Asset asset, DisposalRequest request) {
        disposal.setAsset(asset);
        disposal.setDisposalDate(request.disposalDate());
        disposal.setDisposalMethod(request.disposalMethod());
        disposal.setBuyerVendorId(request.buyerVendorId());
        disposal.setBuyerVendorName(trim(request.buyerVendorName()));
        disposal.setReferenceNumber(trim(request.referenceNumber()));
        disposal.setReason(StringUtils.hasText(request.reason()) ? request.reason().trim() : null);
        disposal.setGainLossAccount(trim(request.gainLossAccount()));
        disposal.setDepreciationMethod(asset.getDepreciationMethod());
        disposal.setAccumulatedDepreciation(money(asset.getAccumulatedDepreciation()));
        disposal.setNetBookValue(money(asset.getNetBookValue()));
        disposal.setDisposalValue(request.disposalValue() == null ? ZERO : money(request.disposalValue()));
        disposal.setGainLossAmount(disposal.getDisposalValue().subtract(disposal.getNetBookValue()));
        disposal.setGainLossPercentage(disposal.getNetBookValue().compareTo(BigDecimal.ZERO) == 0 ? ZERO
                : disposal.getGainLossAmount().multiply(BigDecimal.valueOf(100))
                .divide(disposal.getNetBookValue(), 2, RoundingMode.HALF_UP));
        disposal.setRemarks(trim(request.remarks()));
        disposal.setStatus(request.draft() ? DisposalStatus.DRAFT : DisposalStatus.PENDING_APPROVAL);
    }

    private Map<String, Object> categorySummary(AssetCategory category) {
        return map("id", category.getId(), "categoryCode", category.getCategoryCode(), "categoryName", category.getCategoryName(),
                "description", category.getDescription(), "defaultDepreciationMethod", category.getDefaultDepreciationMethod(),
                "usefulLifeYears", category.getUsefulLifeYears(), "residualValuePercentage", category.getResidualValuePercentage(),
                "depreciationFrequency", category.getDepreciationFrequency(), "defaultAssetCondition", category.getDefaultAssetCondition(),
                "displayColor", category.getDisplayColor(), "status", category.getStatus(),
                "assetCount", assetRepository.countByOrganizationIdAndCategoryIdAndDeletedFalse(ORGANIZATION_ID, category.getId()));
    }

    private Map<String, Object> categoryDetails(AssetCategory category) {
        Map<String, Object> result = categorySummary(category);
        result.put("notes", category.getNotes());
        result.put("createdAt", category.getCreatedAt());
        result.put("updatedAt", category.getUpdatedAt());
        return result;
    }

    private Map<String, Object> assetSummary(Asset asset) {
        return map("id", asset.getId(), "assetNumber", asset.getAssetNumber(), "assetName", asset.getAssetName(),
                "categoryId", asset.getCategory() == null ? null : asset.getCategory().getId(),
                "categoryName", asset.getCategory() == null ? null : asset.getCategory().getCategoryName(),
                "subCategory", asset.getSubCategory(), "assetOwner", asset.getAssetOwner(),
                "assetOwnerLabel", asset.getAssetOwner() == null ? null : title(asset.getAssetOwner().name()),
                "status", asset.getStatus(), "condition", asset.getAssetCondition(),
                "locationName", asset.getLocationName(), "purchaseDate", asset.getPurchaseDate(), "purchaseValue", asset.getPurchaseValue(),
                "currentValue", asset.getCurrentValue(), "netBookValue", asset.getNetBookValue(), "serialNumber", asset.getSerialNumber(),
                "barcode", asset.getBarcode(), "assignedResourceId", asset.getAssignedResourceId(),
                "assignedResourceName", asset.getAssignedResourceName(), "imageUrl", asset.getImageUrl(), "draft", asset.isDraft());
    }

    private Map<String, Object> assetDetails(Asset asset) {
        Map<String, Object> result = assetSummary(asset);
        result.putAll(map("assetType", asset.getAssetType(), "brand", asset.getBrand(), "model", asset.getModel(),
                "quantity", asset.getQuantity(), "unit", asset.getUnit(), "vendorId", asset.getVendor() == null ? null : asset.getVendor().getId(),
                "vendorName", asset.getVendorName(), "invoiceNumber", asset.getInvoiceNumber(), "poNumber", asset.getPoNumber(),
                "taxAmount", asset.getTaxAmount(), "totalAmount", asset.getTotalAmount(), "paymentMethod", asset.getPaymentMethod(),
                "warrantyExpiry", asset.getWarrantyExpiry(), "departmentName", asset.getDepartmentName(), "floorRoom", asset.getFloorRoom(),
                "costCenter", asset.getCostCenter(), "ownershipType", asset.getOwnershipType(), "leaseStartDate", asset.getLeaseStartDate(),
                "leaseEndDate", asset.getLeaseEndDate(), "manufacturer", asset.getManufacturer(), "manufactureYear", asset.getManufactureYear(),
                "countryOfOrigin", asset.getCountryOfOrigin(), "hsnSacCode", asset.getHsnSacCode(), "usefulLifeMonths", asset.getUsefulLifeMonths(),
                "notes", asset.getNotes(), "depreciationMethod", asset.getDepreciationMethod(),
                "depreciationStartDate", asset.getDepreciationStartDate(), "depreciationFrequency", asset.getDepreciationFrequency(),
                "residualValue", asset.getResidualValue(), "scrapValue", asset.getScrapValue(), "capitalizationDate", asset.getCapitalizationDate(),
                "accumulatedDepreciation", asset.getAccumulatedDepreciation(), "createdAt", asset.getCreatedAt(), "updatedAt", asset.getUpdatedAt()));
        return result;
    }

    private Map<String, Object> assignmentSummary(AssetAssignment assignment) {
        return map("id", assignment.getId(), "assetId", assignment.getAsset().getId(), "assetNumber", assignment.getAsset().getAssetNumber(),
                "assetName", assignment.getAsset().getAssetName(), "resourceId", assignment.getResourceId(), "resourceName", assignment.getResourceName(),
                "resourceEmail", assignment.getResourceEmail(), "assignmentDate", assignment.getAssignmentDate(),
                "expectedReturnDate", assignment.getExpectedReturnDate(), "actualReturnDate", assignment.getActualReturnDate(),
                "purpose", assignment.getPurpose(), "costCenter", assignment.getCostCenter(), "projectName", assignment.getProjectName(),
                "referenceNumber", assignment.getReferenceNumber(), "notes", assignment.getNotes(), "status", assignment.getStatus(),
                "createdAt", assignment.getCreatedAt());
    }

    private Map<String, Object> maintenanceSummary(AssetMaintenance maintenance) {
        return map("id", maintenance.getId(), "workOrderNumber", maintenance.getWorkOrderNumber(), "assetId", maintenance.getAsset().getId(),
                "assetNumber", maintenance.getAsset().getAssetNumber(), "assetName", maintenance.getAsset().getAssetName(),
                "categoryName", maintenance.getAsset().getCategory() == null ? null : maintenance.getAsset().getCategory().getCategoryName(),
                "maintenanceType", maintenance.getMaintenanceType(), "scheduledDate", maintenance.getScheduledDate(), "dueDate", maintenance.getDueDate(),
                "priority", maintenance.getPriority(), "status", effectiveMaintenanceStatus(maintenance),
                "technicianResourceId", maintenance.getTechnicianResourceId(), "technicianResourceName", maintenance.getTechnicianResourceName(),
                "estimatedCost", maintenance.getEstimatedCost(), "actualCost", maintenance.getActualCost(), "nextDueDate", maintenance.getNextDueDate());
    }

    private Map<String, Object> maintenanceDetails(AssetMaintenance maintenance) {
        Map<String, Object> result = maintenanceSummary(maintenance);
        result.putAll(map("description", maintenance.getDescription(), "checklistJson", maintenance.getChecklistJson(),
                "assistantResourceId", maintenance.getAssistantResourceId(), "assistantResourceName", maintenance.getAssistantResourceName(),
                "completedDate", maintenance.getCompletedDate(), "estimatedDurationHours", maintenance.getEstimatedDurationHours(),
                "actualDurationHours", maintenance.getActualDurationHours(), "partsCost", maintenance.getPartsCost(), "labourCost", maintenance.getLabourCost(),
                "repeatFrequency", maintenance.getRepeatFrequency(), "notes", maintenance.getNotes(), "attachmentName", maintenance.getAttachmentName(),
                "attachmentUrl", maintenance.getAttachmentUrl(), "asset", assetSummary(maintenance.getAsset()), "createdAt", maintenance.getCreatedAt(),
                "updatedAt", maintenance.getUpdatedAt()));
        return result;
    }

    private Map<String, Object> depreciationSummary(AssetDepreciationSchedule schedule) {
        Asset asset = schedule.getAsset();
        return map("id", schedule.getId(), "assetId", asset.getId(), "assetNumber", asset.getAssetNumber(), "assetName", asset.getAssetName(),
                "categoryName", asset.getCategory() == null ? null : asset.getCategory().getCategoryName(), "purchaseDate", asset.getPurchaseDate(),
                "purchaseValue", asset.getPurchaseValue(), "method", schedule.getMethod(), "financialYear", schedule.getFinancialYear(),
                "usefulLifeMonths", asset.getUsefulLifeMonths(), "accumulatedDepreciation", asset.getAccumulatedDepreciation(),
                "netBookValue", asset.getNetBookValue(), "status", schedule.getStatus(), "nextRunDate", schedule.getNextRunDate());
    }

    private Map<String, Object> depreciationDetails(AssetDepreciationSchedule schedule) {
        Map<String, Object> result = depreciationSummary(schedule);
        result.putAll(map("startDate", schedule.getStartDate(), "endDate", schedule.getEndDate(), "frequency", schedule.getFrequency(),
                "numberOfPeriods", schedule.getNumberOfPeriods(), "residualValueType", schedule.getResidualValueType(),
                "residualValue", schedule.getResidualValue(), "depreciableAmount", schedule.getDepreciableAmount(),
                "depreciationPerPeriod", schedule.getDepreciationPerPeriod(), "expenseAccount", schedule.getExpenseAccount(),
                "accumulatedAccount", schedule.getAccumulatedAccount(), "proRataConvention", schedule.getProRataConvention(),
                "depreciateInPurchaseMonth", schedule.isDepreciateInPurchaseMonth(), "includeInRun", schedule.isIncludeInRun(),
                "description", schedule.getDescription(), "lastRunAt", schedule.getLastRunAt(), "asset", assetSummary(schedule.getAsset())));
        return result;
    }

    private Map<String, Object> entrySummary(AssetDepreciationEntry entry) {
        return map("id", entry.getId(), "financialYear", entry.getFinancialYear(), "periodNumber", entry.getPeriodNumber(),
                "periodLabel", entry.getPeriodLabel(), "startDate", entry.getStartDate(), "endDate", entry.getEndDate(),
                "openingBookValue", entry.getOpeningBookValue(), "depreciationAmount", entry.getDepreciationAmount(),
                "accumulatedDepreciation", entry.getAccumulatedDepreciation(), "closingBookValue", entry.getClosingBookValue(), "status", entry.getStatus());
    }

    private Map<String, Object> disposalSummary(AssetDisposal disposal) {
        Asset asset = disposal.getAsset();
        return map("id", disposal.getId(), "disposalNumber", disposal.getDisposalNumber(), "assetId", asset.getId(),
                "assetNumber", asset.getAssetNumber(), "assetName", asset.getAssetName(),
                "categoryName", asset.getCategory() == null ? null : asset.getCategory().getCategoryName(), "disposalDate", disposal.getDisposalDate(),
                "disposalMethod", disposal.getDisposalMethod(), "disposalValue", disposal.getDisposalValue(),
                "gainLossAmount", disposal.getGainLossAmount(), "gainLossPercentage", disposal.getGainLossPercentage(),
                "status", disposal.getStatus(), "approvedBy", disposal.getApprovedBy());
    }

    private Map<String, Object> disposalDetails(AssetDisposal disposal) {
        Map<String, Object> result = disposalSummary(disposal);
        result.putAll(map("buyerVendorId", disposal.getBuyerVendorId(), "buyerVendorName", disposal.getBuyerVendorName(),
                "referenceNumber", disposal.getReferenceNumber(), "reason", disposal.getReason(), "gainLossAccount", disposal.getGainLossAccount(),
                "depreciationMethod", disposal.getDepreciationMethod(), "accumulatedDepreciation", disposal.getAccumulatedDepreciation(),
                "netBookValue", disposal.getNetBookValue(), "remarks", disposal.getRemarks(), "approvedAt", disposal.getApprovedAt(),
                "approvalNotes", disposal.getApprovalNotes(), "completedAt", disposal.getCompletedAt(), "reversedAt", disposal.getReversedAt(),
                "asset", assetDetails(disposal.getAsset()), "createdAt", disposal.getCreatedAt(), "updatedAt", disposal.getUpdatedAt()));
        return result;
    }

    private Map<String, Object> historySummary(AssetHistory history) {
        return map("id", history.getId(), "eventType", history.getEventType(), "fromStatus", history.getFromStatus(),
                "toStatus", history.getToStatus(), "referenceType", history.getReferenceType(), "referenceId", history.getReferenceId(),
                "description", history.getDescription(), "actor", history.getActor(), "createdAt", history.getCreatedAt());
    }

    private void history(Asset asset, String event, AssetStatus from, AssetStatus to, String description) {
        AssetHistory history = new AssetHistory();
        history.setOrganizationId(ORGANIZATION_ID);
        history.setAsset(asset);
        history.setEventType(event);
        history.setFromStatus(from == null ? null : from.name());
        history.setToStatus(to == null ? null : to.name());
        history.setDescription(description);
        historyRepository.save(history);
    }

    private Asset findAsset(Long id) {
        return assetRepository.findByIdAndOrganizationIdAndDeletedFalse(id, ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found."));
    }

    private AssetCategory findCategory(Long id) {
        return categoryRepository.findByIdAndOrganizationIdAndDeletedFalse(id, ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Asset category not found."));
    }

    private AssetMaintenance findMaintenance(Long id) {
        return maintenanceRepository.findByIdAndOrganizationIdAndDeletedFalse(id, ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance record not found."));
    }

    private AssetDepreciationSchedule findDepreciation(Long id) {
        return depreciationRepository.findByIdAndOrganizationIdAndDeletedFalse(id, ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Depreciation schedule not found."));
    }

    private AssetDisposal findDisposal(Long id) {
        return disposalRepository.findByIdAndOrganizationIdAndDeletedFalse(id, ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Asset disposal not found."));
    }

    private Specification<Asset> baseAssetSpec() {
        return (root, query, cb) -> cb.and(cb.equal(root.get("organizationId"), ORGANIZATION_ID), cb.isFalse(root.get("deleted")));
    }

    private long countAsset(AssetStatus status) {
        return assetRepository.countByOrganizationIdAndStatusAndDeletedFalseAndDraftFalse(ORGANIZATION_ID, status);
    }

    private String nextCategoryCode(String name) {
        String base = name.replaceAll("[^A-Za-z0-9 ]", "").trim().toUpperCase(Locale.ROOT);
        String code = base.contains(" ") ? base.chars().filter(Character::isUpperCase).collect(StringBuilder::new,
                StringBuilder::appendCodePoint, StringBuilder::append).toString() : base.substring(0, Math.min(3, base.length()));
        if (code.isBlank()) code = "AST";
        String candidate = code;
        int suffix = 2;
        while (categoryRepository.existsByOrganizationIdAndCategoryCodeIgnoreCaseAndDeletedFalse(ORGANIZATION_ID, candidate)) {
            candidate = code + suffix++;
        }
        return candidate;
    }

    private String nextAssetNumber(AssetCategory category) {
        String prefix = "AST-" + category.getCategoryCode() + "-";
        List<String> numbers = assetRepository.findRecentNumbers(ORGANIZATION_ID, prefix, Pageable.ofSize(1));
        int next = 1;
        if (!numbers.isEmpty()) {
            String digits = numbers.get(0).substring(prefix.length()).replaceAll("\\D", "");
            if (!digits.isBlank()) next = Integer.parseInt(digits) + 1;
        }
        return prefix + String.format("%04d", next);
    }

    private MaintenanceStatus effectiveMaintenanceStatus(AssetMaintenance maintenance) {
        if (maintenance.getStatus() == MaintenanceStatus.SCHEDULED && maintenance.getDueDate() != null) {
            if (maintenance.getDueDate().isBefore(LocalDate.now())) return MaintenanceStatus.OVERDUE;
            if (maintenance.getDueDate().isEqual(LocalDate.now())) return MaintenanceStatus.DUE_TODAY;
        }
        return maintenance.getStatus();
    }

    private LocalDate periodEnd(LocalDate start, Frequency frequency) {
        return switch (frequency) {
            case MONTHLY -> start.plusMonths(1).minusDays(1);
            case QUARTERLY -> start.plusMonths(3).minusDays(1);
            case HALF_YEARLY -> start.plusMonths(6).minusDays(1);
            case YEARLY -> start.plusYears(1).minusDays(1);
            case NONE -> start;
        };
    }

    private List<Map<String, Object>> chart(Map<String, Long> values) {
        return values.entrySet().stream().map(entry -> map("label", title(entry.getKey()), "value", entry.getValue())).toList();
    }

    private List<Map<String, Object>> assetValueTrend(List<Asset> assets, LocalDate from, LocalDate to) {
        LocalDate rangeEnd = to == null ? LocalDate.now() : to;
        LocalDate rangeStart = from == null ? rangeEnd.minusMonths(11).withDayOfMonth(1) : from;
        if (rangeStart.isAfter(rangeEnd)) {
            LocalDate swap = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = swap;
        }

        YearMonth first = YearMonth.from(rangeStart);
        YearMonth last = YearMonth.from(rangeEnd);
        if (ChronoUnit.MONTHS.between(first, last) > 23) first = last.minusMonths(23);

        List<Map<String, Object>> points = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            LocalDate monthEnd = month.atEndOfMonth();
            BigDecimal value = assets.stream()
                    .filter(asset -> asset.getPurchaseDate() == null || !asset.getPurchaseDate().isAfter(monthEnd))
                    .map(Asset::getCurrentValue)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(map("label", month.getMonth().name().substring(0, 3), "month", month.toString(), "value", money(value)));
        }
        return points;
    }

    private static String title(String value) {
        if (value == null) return "";
        String spaced = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value)) return null;
        try {
            return Enum.valueOf(type, value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return result;
    }
}
