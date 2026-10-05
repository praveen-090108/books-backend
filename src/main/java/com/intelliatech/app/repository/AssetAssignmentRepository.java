package com.intelliatech.app.repository;

import com.intelliatech.app.entity.AssetAssignment;
import com.intelliatech.app.entity.AssetEnums;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, Long>, JpaSpecificationExecutor<AssetAssignment> {
    Optional<AssetAssignment> findByIdAndOrganizationId(Long id, Long organizationId);
    Optional<AssetAssignment> findFirstByOrganizationIdAndAssetIdAndStatus(Long organizationId, Long assetId, AssetEnums.AssignmentStatus status);
    List<AssetAssignment> findAllByOrganizationIdAndAssetIdOrderByCreatedAtDesc(Long organizationId, Long assetId);
}
