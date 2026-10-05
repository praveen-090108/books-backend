package com.intelliatech.app.repository;

import com.intelliatech.app.entity.AssetDepreciationSchedule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetDepreciationScheduleRepository extends JpaRepository<AssetDepreciationSchedule, Long>, JpaSpecificationExecutor<AssetDepreciationSchedule> {
    Optional<AssetDepreciationSchedule> findByIdAndOrganizationIdAndDeletedFalse(Long id, Long organizationId);
    Optional<AssetDepreciationSchedule> findFirstByOrganizationIdAndAssetIdAndDeletedFalseOrderByCreatedAtDesc(Long organizationId, Long assetId);
    List<AssetDepreciationSchedule> findAllByOrganizationIdAndAssetIdAndDeletedFalseOrderByCreatedAtDesc(Long organizationId, Long assetId);
}
