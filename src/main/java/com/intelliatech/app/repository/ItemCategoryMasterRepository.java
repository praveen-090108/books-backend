package com.intelliatech.app.repository;

import com.intelliatech.app.entity.ItemCategoryMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCategoryMasterRepository extends JpaRepository<ItemCategoryMaster, Long> {
    List<ItemCategoryMaster> findAllByOrganizationIdOrderByDisplayOrderAscCategoryNameAsc(Long organizationId);
    List<ItemCategoryMaster> findAllByOrganizationIdAndActiveTrueOrderByDisplayOrderAscCategoryNameAsc(Long organizationId);
    Optional<ItemCategoryMaster> findByIdAndOrganizationId(Long id, Long organizationId);
    Optional<ItemCategoryMaster> findByOrganizationIdAndCategoryNameIgnoreCase(Long organizationId, String categoryName);
    boolean existsByOrganizationIdAndCategoryNameIgnoreCase(Long organizationId, String categoryName);
    boolean existsByOrganizationIdAndCategoryNameIgnoreCaseAndIdNot(Long organizationId, String categoryName, Long id);
}
