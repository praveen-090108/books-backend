package com.intelliatech.app.repository;

import com.intelliatech.app.entity.AssetEnums;
import com.intelliatech.app.entity.AssetMaintenance;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetMaintenanceRepository extends JpaRepository<AssetMaintenance, Long>, JpaSpecificationExecutor<AssetMaintenance> {
    Optional<AssetMaintenance> findByIdAndOrganizationIdAndDeletedFalse(Long id, Long organizationId);
    long countByOrganizationIdAndDeletedFalse(Long organizationId);
    long countByOrganizationIdAndStatusAndDeletedFalse(Long organizationId, AssetEnums.MaintenanceStatus status);
    long countByOrganizationIdAndDueDateAndStatusNotInAndDeletedFalse(Long organizationId, LocalDate date, Collection<AssetEnums.MaintenanceStatus> statuses);
    long countByOrganizationIdAndDueDateBetweenAndStatusNotInAndDeletedFalse(Long organizationId, LocalDate start, LocalDate end, Collection<AssetEnums.MaintenanceStatus> statuses);
    List<AssetMaintenance> findAllByOrganizationIdAndDueDateGreaterThanEqualAndStatusNotInAndDeletedFalseOrderByDueDate(
            Long organizationId, LocalDate date, Collection<AssetEnums.MaintenanceStatus> statuses, Pageable pageable);
    List<AssetMaintenance> findAllByOrganizationIdAndAssetIdAndDeletedFalseOrderByScheduledDateDesc(Long organizationId, Long assetId);
}
