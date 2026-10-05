package com.intelliatech.app.repository;

import com.intelliatech.app.entity.AssetDisposal;
import com.intelliatech.app.entity.AssetEnums;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetDisposalRepository extends JpaRepository<AssetDisposal, Long>, JpaSpecificationExecutor<AssetDisposal> {
    Optional<AssetDisposal> findByIdAndOrganizationIdAndDeletedFalse(Long id, Long organizationId);
    Optional<AssetDisposal> findFirstByOrganizationIdAndAssetIdAndStatusNotAndDeletedFalse(Long organizationId, Long assetId, AssetEnums.DisposalStatus status);
    long countByOrganizationIdAndDeletedFalse(Long organizationId);
    long countByOrganizationIdAndStatusAndDeletedFalse(Long organizationId, AssetEnums.DisposalStatus status);
    @Query("select coalesce(sum(d.disposalValue), 0) from AssetDisposal d where d.organizationId = :organizationId and d.status = 'COMPLETED' and d.deleted = false")
    BigDecimal sumCompletedValue(@Param("organizationId") Long organizationId);
    @Query("select coalesce(sum(d.gainLossAmount), 0) from AssetDisposal d where d.organizationId = :organizationId and d.status = 'COMPLETED' and d.deleted = false")
    BigDecimal sumCompletedGainLoss(@Param("organizationId") Long organizationId);
}
