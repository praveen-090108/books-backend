package com.intelliatech.app.repository;

import com.intelliatech.app.entity.AssetCategory;
import com.intelliatech.app.entity.AssetEnums;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long>, JpaSpecificationExecutor<AssetCategory> {
    Optional<AssetCategory> findByIdAndOrganizationIdAndDeletedFalse(Long id, Long organizationId);
    List<AssetCategory> findAllByOrganizationIdAndDeletedFalseOrderByCategoryName(Long organizationId);
    List<AssetCategory> findAllByOrganizationIdAndStatusAndDeletedFalseOrderByCategoryName(Long organizationId, AssetEnums.CategoryStatus status);
    boolean existsByOrganizationIdAndCategoryNameIgnoreCaseAndDeletedFalse(Long organizationId, String name);
    boolean existsByOrganizationIdAndCategoryNameIgnoreCaseAndIdNotAndDeletedFalse(Long organizationId, String name, Long id);
    boolean existsByOrganizationIdAndCategoryCodeIgnoreCaseAndDeletedFalse(Long organizationId, String code);
}
