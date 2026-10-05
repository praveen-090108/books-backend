package com.intelliatech.app.repository;

import com.intelliatech.app.entity.Asset;
import com.intelliatech.app.entity.AssetEnums;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {
    Optional<Asset> findByIdAndOrganizationIdAndDeletedFalse(Long id, Long organizationId);
    boolean existsByOrganizationIdAndAssetNumberAndDeletedFalse(Long organizationId, String assetNumber);
    long countByOrganizationIdAndDeletedFalseAndDraftFalse(Long organizationId);
    long countByOrganizationIdAndStatusAndDeletedFalseAndDraftFalse(Long organizationId, AssetEnums.AssetStatus status);
    long countByOrganizationIdAndCategoryIdAndDeletedFalse(Long organizationId, Long categoryId);
    List<Asset> findAllByOrganizationIdAndStatusInAndDeletedFalseOrderByAssetName(
            Long organizationId, List<AssetEnums.AssetStatus> statuses);

    @Query("select coalesce(sum(a.currentValue), 0) from Asset a where a.organizationId = :organizationId and a.deleted = false and a.draft = false")
    BigDecimal sumCurrentValue(@Param("organizationId") Long organizationId);

    @Query("select coalesce(sum(a.accumulatedDepreciation), 0) from Asset a where a.organizationId = :organizationId and a.deleted = false and a.draft = false")
    BigDecimal sumAccumulatedDepreciation(@Param("organizationId") Long organizationId);

    @Query("select coalesce(sum(a.netBookValue), 0) from Asset a where a.organizationId = :organizationId and a.deleted = false and a.draft = false")
    BigDecimal sumNetBookValue(@Param("organizationId") Long organizationId);

    @Query("select a.assetNumber from Asset a where a.organizationId = :organizationId and a.assetNumber like concat(:prefix, '%') order by a.id desc")
    List<String> findRecentNumbers(@Param("organizationId") Long organizationId, @Param("prefix") String prefix, Pageable pageable);
}
